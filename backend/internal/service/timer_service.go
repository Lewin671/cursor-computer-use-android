package service

import (
	"context"
	"database/sql"
	"fmt"
	"math"
	"time"

	"github.com/google/uuid"

	apperrors "pomodoro-sync/backend/internal/errors"
	"pomodoro-sync/backend/internal/models"
	"pomodoro-sync/backend/internal/repository"
)

const (
	DefaultFocusDurationSec = int64(25 * 60)
)

type VersionConflictError struct {
	CurrentState *models.TimerState
}

func (e *VersionConflictError) Error() string {
	return "timer state version conflict"
}

type TimerService struct {
	timerRepo   *repository.TimerRepository
	historyRepo *repository.HistoryRepository
}

func NewTimerService(timerRepo *repository.TimerRepository, historyRepo *repository.HistoryRepository) *TimerService {
	return &TimerService{
		timerRepo:   timerRepo,
		historyRepo: historyRepo,
	}
}

func (s *TimerService) GetState(ctx context.Context, userID string) (*models.TimerState, error) {
	now := time.Now().Unix()
	tx, err := s.timerRepo.BeginTx(ctx)
	if err != nil {
		return nil, err
	}
	defer func() { _ = tx.Rollback() }()

	state, err := s.getOrCreateStateTx(ctx, tx, userID, now)
	if err != nil {
		return nil, err
	}
	if err := s.finalizeIfExpiredTx(ctx, tx, state, now); err != nil {
		return nil, err
	}

	if err := tx.Commit(); err != nil {
		return nil, fmt.Errorf("commit get state tx: %w", err)
	}
	return renderState(state, now), nil
}

func (s *TimerService) Start(ctx context.Context, userID, mode string, durationSec, clientVersion int64) (*models.TimerState, error) {
	if !isValidMode(mode) {
		return nil, apperrors.BadRequest("invalid_mode", "mode must be one of focus/short_break/long_break")
	}
	if durationSec < 60 || durationSec > 4*60*60 {
		return nil, apperrors.BadRequest("invalid_duration", "durationSec must be between 60 and 14400")
	}
	return s.mutateWithVersion(ctx, userID, clientVersion, func(state *models.TimerState, now int64, tx *sql.Tx) error {
		startedAt := now
		endAt := now + durationSec
		state.Mode = mode
		state.Status = models.TimerStatusRunning
		state.DurationSec = durationSec
		state.RemainingSec = durationSec
		state.StartedAt = &startedAt
		state.EndAt = &endAt
		return nil
	})
}

func (s *TimerService) Pause(ctx context.Context, userID string, clientVersion int64) (*models.TimerState, error) {
	return s.mutateWithVersion(ctx, userID, clientVersion, func(state *models.TimerState, now int64, tx *sql.Tx) error {
		if state.Status != models.TimerStatusRunning || state.EndAt == nil {
			return nil
		}
		remaining := int64(math.Max(0, float64(*state.EndAt-now)))
		state.Status = models.TimerStatusPaused
		state.RemainingSec = remaining
		state.StartedAt = nil
		state.EndAt = nil
		return nil
	})
}

func (s *TimerService) Reset(ctx context.Context, userID string, clientVersion int64) (*models.TimerState, error) {
	return s.mutateWithVersion(ctx, userID, clientVersion, func(state *models.TimerState, now int64, tx *sql.Tx) error {
		state.Status = models.TimerStatusIdle
		state.Mode = models.TimerModeFocus
		if state.DurationSec <= 0 {
			state.DurationSec = DefaultFocusDurationSec
		}
		state.RemainingSec = state.DurationSec
		state.StartedAt = nil
		state.EndAt = nil
		return nil
	})
}

func (s *TimerService) Complete(ctx context.Context, userID string, clientVersion int64) (*models.TimerState, error) {
	return s.mutateWithVersion(ctx, userID, clientVersion, func(state *models.TimerState, now int64, tx *sql.Tx) error {
		if state.Status == models.TimerStatusIdle {
			return nil
		}
		startedAt := now - state.DurationSec
		if state.StartedAt != nil {
			startedAt = *state.StartedAt
		}
		history := models.FocusHistory{
			ID:                 uuid.NewString(),
			UserID:             userID,
			Mode:               state.Mode,
			DurationSec:        state.DurationSec,
			StartedAt:          startedAt,
			EndedAt:            now,
			SourceTimerVersion: state.Version,
			CreatedAt:          now,
		}
		if err := s.historyRepo.CreateTx(ctx, tx, history); err != nil {
			return err
		}

		state.Status = models.TimerStatusIdle
		state.RemainingSec = state.DurationSec
		state.StartedAt = nil
		state.EndAt = nil
		return nil
	})
}

func (s *TimerService) ListHistory(ctx context.Context, userID string, since int64, limit int) ([]models.FocusHistory, error) {
	if limit <= 0 || limit > 200 {
		limit = 100
	}
	return s.historyRepo.ListSince(ctx, userID, since, limit)
}

func (s *TimerService) mutateWithVersion(
	ctx context.Context,
	userID string,
	clientVersion int64,
	mutation func(state *models.TimerState, now int64, tx *sql.Tx) error,
) (*models.TimerState, error) {
	now := time.Now().Unix()
	tx, err := s.timerRepo.BeginTx(ctx)
	if err != nil {
		return nil, err
	}
	defer func() { _ = tx.Rollback() }()

	state, err := s.getOrCreateStateTx(ctx, tx, userID, now)
	if err != nil {
		return nil, err
	}
	if err := s.finalizeIfExpiredTx(ctx, tx, state, now); err != nil {
		return nil, err
	}
	if state.Version != clientVersion {
		return nil, &VersionConflictError{CurrentState: renderState(state, now)}
	}

	prevState := *state
	if err := mutation(state, now, tx); err != nil {
		return nil, err
	}
	if stateEqual(prevState, *state) {
		if err := tx.Commit(); err != nil {
			return nil, fmt.Errorf("commit noop mutation: %w", err)
		}
		return renderState(state, now), nil
	}

	state.Version++
	state.UpdatedAt = now
	if err := s.timerRepo.UpdateTx(ctx, tx, *state); err != nil {
		return nil, err
	}

	if err := tx.Commit(); err != nil {
		return nil, fmt.Errorf("commit timer mutation: %w", err)
	}
	return renderState(state, now), nil
}

func (s *TimerService) getOrCreateStateTx(ctx context.Context, tx *sql.Tx, userID string, now int64) (*models.TimerState, error) {
	state, err := s.timerRepo.GetByUserIDTx(ctx, tx, userID)
	if err != nil {
		return nil, err
	}
	if state != nil {
		return state, nil
	}
	defaultState := models.TimerState{
		UserID:       userID,
		Mode:         models.TimerModeFocus,
		Status:       models.TimerStatusIdle,
		DurationSec:  DefaultFocusDurationSec,
		RemainingSec: DefaultFocusDurationSec,
		Version:      1,
		UpdatedAt:    now,
	}
	if err := s.timerRepo.CreateTx(ctx, tx, defaultState); err != nil {
		return nil, err
	}
	stateCopy := defaultState
	return &stateCopy, nil
}

func (s *TimerService) finalizeIfExpiredTx(ctx context.Context, tx *sql.Tx, state *models.TimerState, now int64) error {
	if state.Status != models.TimerStatusRunning || state.EndAt == nil {
		return nil
	}
	if *state.EndAt > now {
		return nil
	}

	startedAt := now - state.DurationSec
	if state.StartedAt != nil {
		startedAt = *state.StartedAt
	}
	history := models.FocusHistory{
		ID:                 uuid.NewString(),
		UserID:             state.UserID,
		Mode:               state.Mode,
		DurationSec:        state.DurationSec,
		StartedAt:          startedAt,
		EndedAt:            *state.EndAt,
		SourceTimerVersion: state.Version,
		CreatedAt:          now,
	}
	if err := s.historyRepo.CreateTx(ctx, tx, history); err != nil {
		return err
	}

	state.Status = models.TimerStatusIdle
	state.StartedAt = nil
	state.EndAt = nil
	state.RemainingSec = state.DurationSec
	state.Version++
	state.UpdatedAt = now
	if err := s.timerRepo.UpdateTx(ctx, tx, *state); err != nil {
		return err
	}
	return nil
}

func renderState(in *models.TimerState, now int64) *models.TimerState {
	out := *in
	if out.Status == models.TimerStatusRunning && out.EndAt != nil {
		out.RemainingSec = int64(math.Max(0, float64(*out.EndAt-now)))
	}
	return &out
}

func isValidMode(mode string) bool {
	return mode == models.TimerModeFocus || mode == models.TimerModeShortBreak || mode == models.TimerModeLongBreak
}

func stateEqual(a models.TimerState, b models.TimerState) bool {
	return a.Mode == b.Mode &&
		a.Status == b.Status &&
		a.DurationSec == b.DurationSec &&
		a.RemainingSec == b.RemainingSec &&
		equalInt64Ptr(a.StartedAt, b.StartedAt) &&
		equalInt64Ptr(a.EndAt, b.EndAt)
}

func equalInt64Ptr(a, b *int64) bool {
	if a == nil && b == nil {
		return true
	}
	if a == nil || b == nil {
		return false
	}
	return *a == *b
}
