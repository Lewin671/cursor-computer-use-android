package repository

import (
	"context"
	"database/sql"
	"fmt"

	"pomodoro-sync/backend/internal/models"
)

type HistoryRepository struct {
	db *sql.DB
}

func NewHistoryRepository(db *sql.DB) *HistoryRepository {
	return &HistoryRepository{db: db}
}

func (r *HistoryRepository) CreateTx(ctx context.Context, tx *sql.Tx, item models.FocusHistory) error {
	_, err := tx.ExecContext(
		ctx,
		`INSERT INTO focus_histories(id, user_id, mode, duration_sec, started_at, ended_at, source_timer_version, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
		item.ID,
		item.UserID,
		item.Mode,
		item.DurationSec,
		item.StartedAt,
		item.EndedAt,
		item.SourceTimerVersion,
		item.CreatedAt,
	)
	if err != nil {
		return fmt.Errorf("insert history: %w", err)
	}
	return nil
}

func (r *HistoryRepository) ListSince(ctx context.Context, userID string, since int64, limit int) ([]models.FocusHistory, error) {
	rows, err := r.db.QueryContext(
		ctx,
		`SELECT id, user_id, mode, duration_sec, started_at, ended_at, source_timer_version, created_at
         FROM focus_histories
         WHERE user_id = ? AND created_at > ?
         ORDER BY created_at ASC
         LIMIT ?`,
		userID,
		since,
		limit,
	)
	if err != nil {
		return nil, fmt.Errorf("query history: %w", err)
	}
	defer rows.Close()

	items := make([]models.FocusHistory, 0)
	for rows.Next() {
		var item models.FocusHistory
		if err := rows.Scan(
			&item.ID,
			&item.UserID,
			&item.Mode,
			&item.DurationSec,
			&item.StartedAt,
			&item.EndedAt,
			&item.SourceTimerVersion,
			&item.CreatedAt,
		); err != nil {
			return nil, fmt.Errorf("scan history: %w", err)
		}
		items = append(items, item)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate history: %w", err)
	}
	return items, nil
}
