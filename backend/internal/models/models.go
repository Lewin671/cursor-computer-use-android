package models

const (
	TimerModeFocus      = "focus"
	TimerModeShortBreak = "short_break"
	TimerModeLongBreak  = "long_break"
)

const (
	TimerStatusIdle    = "idle"
	TimerStatusRunning = "running"
	TimerStatusPaused  = "paused"
)

type User struct {
	ID           string `json:"id"`
	Email        string `json:"email"`
	PasswordHash string `json:"-"`
	CreatedAt    int64  `json:"createdAt"`
	UpdatedAt    int64  `json:"updatedAt"`
}

type TimerState struct {
	UserID       string `json:"userId"`
	Mode         string `json:"mode"`
	Status       string `json:"status"`
	DurationSec  int64  `json:"durationSec"`
	RemainingSec int64  `json:"remainingSec"`
	StartedAt    *int64 `json:"startedAt,omitempty"`
	EndAt        *int64 `json:"endAt,omitempty"`
	Version      int64  `json:"version"`
	UpdatedAt    int64  `json:"updatedAt"`
}

type FocusHistory struct {
	ID                 string `json:"id"`
	UserID             string `json:"userId"`
	Mode               string `json:"mode"`
	DurationSec        int64  `json:"durationSec"`
	StartedAt          int64  `json:"startedAt"`
	EndedAt            int64  `json:"endedAt"`
	SourceTimerVersion int64  `json:"sourceTimerVersion"`
	CreatedAt          int64  `json:"createdAt"`
}

type RefreshToken struct {
	ID        string
	UserID    string
	TokenHash string
	ExpiresAt int64
	CreatedAt int64
	RevokedAt *int64
}
