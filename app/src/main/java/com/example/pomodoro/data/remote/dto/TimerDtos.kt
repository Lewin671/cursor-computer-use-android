package com.example.pomodoro.data.remote.dto

data class TimerStateDto(
    val userId: String,
    val mode: String,
    val status: String,
    val durationSec: Long,
    val remainingSec: Long,
    val startedAt: Long?,
    val endAt: Long?,
    val version: Long,
    val updatedAt: Long
)

data class StartTimerRequest(
    val mode: String,
    val durationSec: Long,
    val clientVersion: Long
)

data class VersionRequest(
    val clientVersion: Long
)

data class HistoryItemDto(
    val id: String,
    val userId: String,
    val mode: String,
    val durationSec: Long,
    val startedAt: Long,
    val endedAt: Long,
    val sourceTimerVersion: Long,
    val createdAt: Long
)

data class HistoryResponse(
    val items: List<HistoryItemDto>,
    val serverTime: Long
)

data class ApiErrorResponse(
    val code: String? = null,
    val message: String? = null,
    val currentState: TimerStateDto? = null
)
