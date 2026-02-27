package repository

import (
	"context"
	"database/sql"
	"errors"
	"fmt"

	"pomodoro-sync/backend/internal/models"
)

type RefreshTokenRepository struct {
	db *sql.DB
}

func NewRefreshTokenRepository(db *sql.DB) *RefreshTokenRepository {
	return &RefreshTokenRepository{db: db}
}

func (r *RefreshTokenRepository) Create(ctx context.Context, token models.RefreshToken) error {
	_, err := r.db.ExecContext(
		ctx,
		`INSERT INTO refresh_tokens(id, user_id, token_hash, expires_at, created_at, revoked_at) VALUES (?, ?, ?, ?, ?, ?)`,
		token.ID,
		token.UserID,
		token.TokenHash,
		token.ExpiresAt,
		token.CreatedAt,
		token.RevokedAt,
	)
	if err != nil {
		return fmt.Errorf("insert refresh token: %w", err)
	}
	return nil
}

func (r *RefreshTokenRepository) GetByID(ctx context.Context, id string) (*models.RefreshToken, error) {
	row := r.db.QueryRowContext(ctx, `SELECT id, user_id, token_hash, expires_at, created_at, revoked_at FROM refresh_tokens WHERE id = ?`, id)
	var token models.RefreshToken
	var revokedAt sql.NullInt64
	if err := row.Scan(&token.ID, &token.UserID, &token.TokenHash, &token.ExpiresAt, &token.CreatedAt, &revokedAt); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil
		}
		return nil, fmt.Errorf("scan refresh token: %w", err)
	}
	if revokedAt.Valid {
		token.RevokedAt = &revokedAt.Int64
	}
	return &token, nil
}

func (r *RefreshTokenRepository) Revoke(ctx context.Context, id string, now int64) error {
	_, err := r.db.ExecContext(ctx, `UPDATE refresh_tokens SET revoked_at = ? WHERE id = ? AND revoked_at IS NULL`, now, id)
	if err != nil {
		return fmt.Errorf("revoke refresh token: %w", err)
	}
	return nil
}
