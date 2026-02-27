package repository

import (
	"context"
	"database/sql"
	"errors"
	"fmt"

	"pomodoro-sync/backend/internal/models"
)

type TimerRepository struct {
	db *sql.DB
}

func NewTimerRepository(db *sql.DB) *TimerRepository {
	return &TimerRepository{db: db}
}

func (r *TimerRepository) BeginTx(ctx context.Context) (*sql.Tx, error) {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, fmt.Errorf("begin tx: %w", err)
	}
	return tx, nil
}

func (r *TimerRepository) GetByUserIDTx(ctx context.Context, tx *sql.Tx, userID string) (*models.TimerState, error) {
	row := tx.QueryRowContext(ctx, `SELECT user_id, mode, status, duration_sec, remaining_sec, started_at, end_at, version, updated_at FROM timer_states WHERE user_id = ?`, userID)
	var state models.TimerState
	var startedAt sql.NullInt64
	var endAt sql.NullInt64
	if err := row.Scan(&state.UserID, &state.Mode, &state.Status, &state.DurationSec, &state.RemainingSec, &startedAt, &endAt, &state.Version, &state.UpdatedAt); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil
		}
		return nil, fmt.Errorf("scan timer state: %w", err)
	}
	if startedAt.Valid {
		state.StartedAt = &startedAt.Int64
	}
	if endAt.Valid {
		state.EndAt = &endAt.Int64
	}
	return &state, nil
}

func (r *TimerRepository) CreateTx(ctx context.Context, tx *sql.Tx, state models.TimerState) error {
	_, err := tx.ExecContext(
		ctx,
		`INSERT INTO timer_states(user_id, mode, status, duration_sec, remaining_sec, started_at, end_at, version, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		state.UserID,
		state.Mode,
		state.Status,
		state.DurationSec,
		state.RemainingSec,
		state.StartedAt,
		state.EndAt,
		state.Version,
		state.UpdatedAt,
	)
	if err != nil {
		return fmt.Errorf("insert timer state: %w", err)
	}
	return nil
}

func (r *TimerRepository) UpdateTx(ctx context.Context, tx *sql.Tx, state models.TimerState) error {
	_, err := tx.ExecContext(
		ctx,
		`UPDATE timer_states
         SET mode = ?, status = ?, duration_sec = ?, remaining_sec = ?, started_at = ?, end_at = ?, version = ?, updated_at = ?
         WHERE user_id = ?`,
		state.Mode,
		state.Status,
		state.DurationSec,
		state.RemainingSec,
		state.StartedAt,
		state.EndAt,
		state.Version,
		state.UpdatedAt,
		state.UserID,
	)
	if err != nil {
		return fmt.Errorf("update timer state: %w", err)
	}
	return nil
}
