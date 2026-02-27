package service

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
	"golang.org/x/crypto/bcrypt"

	"pomodoro-sync/backend/internal/config"
	apperrors "pomodoro-sync/backend/internal/errors"
	"pomodoro-sync/backend/internal/models"
	"pomodoro-sync/backend/internal/repository"
)

type AuthTokens struct {
	UserID       string `json:"userId"`
	AccessToken  string `json:"accessToken"`
	RefreshToken string `json:"refreshToken"`
	ExpiresInSec int64  `json:"expiresInSec"`
}

type AuthService struct {
	cfg              config.Config
	userRepo         *repository.UserRepository
	refreshTokenRepo *repository.RefreshTokenRepository
}

func NewAuthService(cfg config.Config, userRepo *repository.UserRepository, refreshTokenRepo *repository.RefreshTokenRepository) *AuthService {
	return &AuthService{
		cfg:              cfg,
		userRepo:         userRepo,
		refreshTokenRepo: refreshTokenRepo,
	}
}

func (s *AuthService) Register(ctx context.Context, email string, password string) (*AuthTokens, error) {
	email = normalizeEmail(email)
	if !strings.Contains(email, "@") {
		return nil, apperrors.BadRequest("invalid_email", "email format is invalid")
	}
	if len(password) < 6 {
		return nil, apperrors.BadRequest("weak_password", "password length must be at least 6")
	}

	existed, err := s.userRepo.GetByEmail(ctx, email)
	if err != nil {
		return nil, fmt.Errorf("check user existence: %w", err)
	}
	if existed != nil {
		return nil, apperrors.New(http.StatusConflict, "email_exists", "email already exists")
	}

	passwordHash, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return nil, fmt.Errorf("hash password: %w", err)
	}
	now := time.Now().Unix()
	user := models.User{
		ID:           uuid.NewString(),
		Email:        email,
		PasswordHash: string(passwordHash),
		CreatedAt:    now,
		UpdatedAt:    now,
	}
	if err := s.userRepo.Create(ctx, user); err != nil {
		return nil, fmt.Errorf("create user: %w", err)
	}
	return s.issueTokens(ctx, user.ID)
}

func (s *AuthService) Login(ctx context.Context, email string, password string) (*AuthTokens, error) {
	email = normalizeEmail(email)
	user, err := s.userRepo.GetByEmail(ctx, email)
	if err != nil {
		return nil, fmt.Errorf("get user: %w", err)
	}
	if user == nil {
		return nil, apperrors.Unauthorized("invalid_credentials", "email or password is incorrect")
	}
	if err := bcrypt.CompareHashAndPassword([]byte(user.PasswordHash), []byte(password)); err != nil {
		return nil, apperrors.Unauthorized("invalid_credentials", "email or password is incorrect")
	}
	return s.issueTokens(ctx, user.ID)
}

func (s *AuthService) Refresh(ctx context.Context, refreshToken string) (*AuthTokens, error) {
	claims := jwt.MapClaims{}
	token, err := jwt.ParseWithClaims(refreshToken, claims, func(token *jwt.Token) (interface{}, error) {
		return []byte(s.cfg.JWTSecret), nil
	})
	if err != nil || !token.Valid {
		return nil, apperrors.Unauthorized("invalid_refresh_token", "refresh token is invalid")
	}
	if claims["token_type"] != "refresh" {
		return nil, apperrors.Unauthorized("invalid_refresh_token", "refresh token type mismatch")
	}
	jti, _ := claims["jti"].(string)
	userID, _ := claims["sub"].(string)
	if jti == "" || userID == "" {
		return nil, apperrors.Unauthorized("invalid_refresh_token", "refresh token payload is invalid")
	}

	tokenRecord, err := s.refreshTokenRepo.GetByID(ctx, jti)
	if err != nil {
		return nil, fmt.Errorf("get refresh token record: %w", err)
	}
	if tokenRecord == nil || tokenRecord.UserID != userID {
		return nil, apperrors.Unauthorized("invalid_refresh_token", "refresh token not found")
	}
	now := time.Now().Unix()
	if tokenRecord.RevokedAt != nil || tokenRecord.ExpiresAt < now {
		return nil, apperrors.Unauthorized("invalid_refresh_token", "refresh token expired or revoked")
	}
	if hash(refreshToken) != tokenRecord.TokenHash {
		return nil, apperrors.Unauthorized("invalid_refresh_token", "refresh token hash mismatch")
	}

	if err := s.refreshTokenRepo.Revoke(ctx, tokenRecord.ID, now); err != nil {
		return nil, fmt.Errorf("revoke used refresh token: %w", err)
	}
	return s.issueTokens(ctx, userID)
}

func (s *AuthService) Logout(ctx context.Context, refreshToken string) error {
	claims := jwt.MapClaims{}
	token, err := jwt.ParseWithClaims(refreshToken, claims, func(token *jwt.Token) (interface{}, error) {
		return []byte(s.cfg.JWTSecret), nil
	})
	if err != nil || !token.Valid {
		return nil
	}
	jti, _ := claims["jti"].(string)
	if jti == "" {
		return nil
	}
	return s.refreshTokenRepo.Revoke(ctx, jti, time.Now().Unix())
}

func (s *AuthService) issueTokens(ctx context.Context, userID string) (*AuthTokens, error) {
	now := time.Now()
	accessExp := now.Add(s.cfg.AccessTokenTTL)
	refreshExp := now.Add(s.cfg.RefreshTokenTTL)

	accessClaims := jwt.MapClaims{
		"sub":        userID,
		"token_type": "access",
		"iat":        now.Unix(),
		"exp":        accessExp.Unix(),
	}
	access := jwt.NewWithClaims(jwt.SigningMethodHS256, accessClaims)
	accessToken, err := access.SignedString([]byte(s.cfg.JWTSecret))
	if err != nil {
		return nil, fmt.Errorf("sign access token: %w", err)
	}

	refreshID := uuid.NewString()
	refreshClaims := jwt.MapClaims{
		"sub":        userID,
		"token_type": "refresh",
		"jti":        refreshID,
		"iat":        now.Unix(),
		"exp":        refreshExp.Unix(),
	}
	refresh := jwt.NewWithClaims(jwt.SigningMethodHS256, refreshClaims)
	refreshToken, err := refresh.SignedString([]byte(s.cfg.JWTSecret))
	if err != nil {
		return nil, fmt.Errorf("sign refresh token: %w", err)
	}
	if err := s.refreshTokenRepo.Create(ctx, models.RefreshToken{
		ID:        refreshID,
		UserID:    userID,
		TokenHash: hash(refreshToken),
		ExpiresAt: refreshExp.Unix(),
		CreatedAt: now.Unix(),
	}); err != nil {
		return nil, fmt.Errorf("store refresh token: %w", err)
	}

	return &AuthTokens{
		UserID:       userID,
		AccessToken:  accessToken,
		RefreshToken: refreshToken,
		ExpiresInSec: int64(s.cfg.AccessTokenTTL.Seconds()),
	}, nil
}

func normalizeEmail(email string) string {
	return strings.ToLower(strings.TrimSpace(email))
}

func hash(raw string) string {
	sum := sha256.Sum256([]byte(raw))
	return hex.EncodeToString(sum[:])
}
