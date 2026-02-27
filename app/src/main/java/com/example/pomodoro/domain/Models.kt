package com.example.pomodoro.domain

enum class TimerMode(val raw: String) {
    Focus("focus"),
    ShortBreak("short_break"),
    LongBreak("long_break");

    companion object {
        fun fromRaw(raw: String): TimerMode {
            return entries.firstOrNull { it.raw == raw } ?: Focus
        }
    }
}

enum class TimerStatus(val raw: String) {
    Idle("idle"),
    Running("running"),
    Paused("paused");

    companion object {
        fun fromRaw(raw: String): TimerStatus {
            return entries.firstOrNull { it.raw == raw } ?: Idle
        }
    }
}

data class TimerState(
    val userId: String,
    val mode: TimerMode = TimerMode.Focus,
    val status: TimerStatus = TimerStatus.Idle,
    val durationSec: Long = 25 * 60,
    val remainingSec: Long = 25 * 60,
    val startedAt: Long? = null,
    val endAt: Long? = null,
    val version: Long = 1,
    val updatedAt: Long = 0
)

data class FocusHistory(
    val id: String,
    val userId: String,
    val mode: TimerMode,
    val durationSec: Long,
    val startedAt: Long,
    val endedAt: Long,
    val sourceTimerVersion: Long,
    val createdAt: Long
)

enum class PendingActionType {
    Start,
    Pause,
    Reset,
    Complete
}
