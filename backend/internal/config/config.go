package config

import (
	"os"
	"strconv"
	"strings"
	"time"
)

type Config struct {
	Addr            string
	DBPath          string
	JWTSecret       string
	AccessTokenTTL  time.Duration
	RefreshTokenTTL time.Duration
	CORSOrigins     []string
}

func Load() Config {
	return Config{
		Addr:            getEnv("APP_ADDR", ":8080"),
		DBPath:          getEnv("DB_PATH", "data/pomodoro.db"),
		JWTSecret:       getEnv("JWT_SECRET", "dev-only-secret-change-me"),
		AccessTokenTTL:  time.Duration(getEnvInt("ACCESS_TOKEN_MINUTES", 15)) * time.Minute,
		RefreshTokenTTL: time.Duration(getEnvInt("REFRESH_TOKEN_HOURS", 24*30)) * time.Hour,
		CORSOrigins:     splitCSV(getEnv("CORS_ORIGINS", "*")),
	}
}

func getEnv(key, fallback string) string {
	v := strings.TrimSpace(os.Getenv(key))
	if v == "" {
		return fallback
	}
	return v
}

func getEnvInt(key string, fallback int) int {
	v := strings.TrimSpace(os.Getenv(key))
	if v == "" {
		return fallback
	}
	parsed, err := strconv.Atoi(v)
	if err != nil {
		return fallback
	}
	return parsed
}

func splitCSV(v string) []string {
	parts := strings.Split(v, ",")
	out := make([]string, 0, len(parts))
	for _, p := range parts {
		trimmed := strings.TrimSpace(p)
		if trimmed == "" {
			continue
		}
		out = append(out, trimmed)
	}
	if len(out) == 0 {
		return []string{"*"}
	}
	return out
}
