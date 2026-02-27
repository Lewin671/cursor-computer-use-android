package com.example.pomodoro.data.repository

import com.example.pomodoro.data.local.entity.FocusHistoryEntity
import com.example.pomodoro.data.local.entity.PendingActionEntity
import com.example.pomodoro.data.local.entity.TimerStateEntity
import com.example.pomodoro.data.remote.dto.HistoryItemDto
import com.example.pomodoro.data.remote.dto.TimerStateDto
import com.example.pomodoro.domain.FocusHistory
import com.example.pomodoro.domain.PendingActionType
import com.example.pomodoro.domain.TimerMode
import com.example.pomodoro.domain.TimerState
import com.example.pomodoro.domain.TimerStatus
import kotlin.math.max

fun TimerStateDto.toEntity(lastSyncedAt: Long): TimerStateEntity = TimerStateEntity(
    userId = userId,
    mode = mode,
    status = status,
    durationSec = durationSec,
    remainingSec = remainingSec,
    startedAt = startedAt,
    endAt = endAt,
    version = version,
    updatedAt = updatedAt,
    lastSyncedAt = lastSyncedAt
)

fun TimerStateEntity.toDomain(nowEpochSec: Long = System.currentTimeMillis() / 1000): TimerState {
    val modeEnum = TimerMode.fromRaw(mode)
    val statusEnum = TimerStatus.fromRaw(status)
    val dynamicRemaining = if (statusEnum == TimerStatus.Running && endAt != null) {
        max(0, endAt - nowEpochSec)
    } else {
        remainingSec
    }
    return TimerState(
        userId = userId,
        mode = modeEnum,
        status = statusEnum,
        durationSec = durationSec,
        remainingSec = dynamicRemaining,
        startedAt = startedAt,
        endAt = endAt,
        version = version,
        updatedAt = updatedAt
    )
}

fun TimerState.toEntity(lastSyncedAt: Long): TimerStateEntity = TimerStateEntity(
    userId = userId,
    mode = mode.raw,
    status = status.raw,
    durationSec = durationSec,
    remainingSec = remainingSec,
    startedAt = startedAt,
    endAt = endAt,
    version = version,
    updatedAt = updatedAt,
    lastSyncedAt = lastSyncedAt
)

fun HistoryItemDto.toEntity(): FocusHistoryEntity = FocusHistoryEntity(
    id = id,
    userId = userId,
    mode = mode,
    durationSec = durationSec,
    startedAt = startedAt,
    endedAt = endedAt,
    sourceTimerVersion = sourceTimerVersion,
    createdAt = createdAt
)

fun FocusHistoryEntity.toDomain(): FocusHistory = FocusHistory(
    id = id,
    userId = userId,
    mode = TimerMode.fromRaw(mode),
    durationSec = durationSec,
    startedAt = startedAt,
    endedAt = endedAt,
    sourceTimerVersion = sourceTimerVersion,
    createdAt = createdAt
)

fun PendingActionEntity.type(): PendingActionType = PendingActionType.valueOf(actionType)
