package com.example.pomodoro.data.repository

import com.example.pomodoro.data.local.dao.FocusHistoryDao
import com.example.pomodoro.data.local.dao.PendingActionDao
import com.example.pomodoro.data.local.dao.TimerStateDao
import com.example.pomodoro.data.local.entity.PendingActionEntity
import com.example.pomodoro.data.remote.PomodoroApi
import com.example.pomodoro.data.remote.dto.ApiErrorResponse
import com.example.pomodoro.data.remote.dto.StartTimerRequest
import com.example.pomodoro.data.remote.dto.TimerStateDto
import com.example.pomodoro.data.remote.dto.VersionRequest
import com.example.pomodoro.domain.FocusHistory
import com.example.pomodoro.domain.PendingActionType
import com.example.pomodoro.domain.TimerMode
import com.example.pomodoro.domain.TimerState
import com.example.pomodoro.domain.TimerStatus
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.io.IOException
import kotlin.math.max

class TimerRepository(
    private val api: PomodoroApi,
    private val timerStateDao: TimerStateDao,
    private val historyDao: FocusHistoryDao,
    private val pendingActionDao: PendingActionDao,
    private val moshi: Moshi
) {
    private val syncMutex = Mutex()
    private val errorAdapter = moshi.adapter(ApiErrorResponse::class.java)

    fun observeTimerState(userId: String): Flow<TimerState?> {
        return timerStateDao.observeByUser(userId).map { entity ->
            entity?.toDomain()
        }
    }

    fun observeHistory(userId: String): Flow<List<FocusHistory>> {
        return historyDao.observeByUser(userId).map { items ->
            items.map { it.toDomain() }
        }
    }

    suspend fun getCurrentState(userId: String): TimerState? {
        return timerStateDao.getByUser(userId)?.toDomain(nowSec())
    }

    suspend fun syncNow(userId: String) {
        syncMutex.withLock {
            flushPendingActionsLocked(userId)
            pullStateLocked(userId)
            pullHistoryLocked(userId)
        }
    }

    suspend fun start(userId: String, mode: TimerMode, durationSec: Long) {
        enqueueOptimisticMutation(
            userId = userId,
            actionType = PendingActionType.Start,
            mode = mode,
            durationSec = durationSec
        ) { current, now ->
            val startedAt = now
            val endAt = now + durationSec
            current.copy(
                mode = mode,
                status = TimerStatus.Running,
                durationSec = durationSec,
                remainingSec = durationSec,
                startedAt = startedAt,
                endAt = endAt
            )
        }
    }

    suspend fun pause(userId: String) {
        enqueueOptimisticMutation(
            userId = userId,
            actionType = PendingActionType.Pause
        ) { current, now ->
            if (current.status != TimerStatus.Running || current.endAt == null) {
                current
            } else {
                current.copy(
                    status = TimerStatus.Paused,
                    remainingSec = max(0, current.endAt - now),
                    startedAt = null,
                    endAt = null
                )
            }
        }
    }

    suspend fun reset(userId: String) {
        enqueueOptimisticMutation(
            userId = userId,
            actionType = PendingActionType.Reset
        ) { current, _ ->
            current.copy(
                mode = TimerMode.Focus,
                status = TimerStatus.Idle,
                remainingSec = current.durationSec,
                startedAt = null,
                endAt = null
            )
        }
    }

    suspend fun complete(userId: String) {
        enqueueOptimisticMutation(
            userId = userId,
            actionType = PendingActionType.Complete
        ) { current, _ ->
            if (current.status == TimerStatus.Idle) {
                current
            } else {
                current.copy(
                    status = TimerStatus.Idle,
                    remainingSec = current.durationSec,
                    startedAt = null,
                    endAt = null
                )
            }
        }
    }

    suspend fun completeIfExpired(userId: String) {
        val current = timerStateDao.getByUser(userId)?.toDomain() ?: return
        val now = nowSec()
        if (current.status == TimerStatus.Running && current.endAt != null && current.endAt <= now) {
            complete(userId)
        }
    }

    private suspend fun enqueueOptimisticMutation(
        userId: String,
        actionType: PendingActionType,
        mode: TimerMode? = null,
        durationSec: Long? = null,
        reducer: (TimerState, Long) -> TimerState
    ) {
        val now = nowSec()
        val current = timerStateDao.getByUser(userId)?.toDomain(now) ?: defaultState(userId, now)
        val reduced = reducer(current, now)
        val optimistic = reduced.copy(
            version = current.version + 1,
            updatedAt = now
        )
        timerStateDao.upsert(optimistic.toEntity(lastSyncedAt = now))
        pendingActionDao.insert(
            PendingActionEntity(
                userId = userId,
                actionType = actionType.name,
                mode = mode?.raw,
                durationSec = durationSec,
                baseVersion = current.version,
                createdAt = now
            )
        )
        syncNow(userId)
    }

    private suspend fun flushPendingActionsLocked(userId: String) {
        val actions = pendingActionDao.listForUser(userId)
        for (action in actions) {
            val response = runAction(action)
            when {
                response.isSuccessful -> {
                    val body = response.body()
                    if (body != null) {
                        timerStateDao.upsert(body.toEntity(lastSyncedAt = nowSec()))
                    }
                    pendingActionDao.deleteById(action.id)
                }

                response.code() == 409 -> {
                    val conflictState = parseConflict(response.errorBody())?.currentState
                    if (conflictState != null) {
                        timerStateDao.upsert(conflictState.toEntity(lastSyncedAt = nowSec()))
                    }
                    pendingActionDao.deleteById(action.id)
                }

                response.code() in 500..599 -> {
                    return
                }

                else -> {
                    // Client request malformed or unauthorized; drop and refresh from server.
                    pendingActionDao.deleteById(action.id)
                }
            }
        }
    }

    private suspend fun runAction(action: PendingActionEntity): Response<TimerStateDto> {
        return try {
            when (action.type()) {
                PendingActionType.Start -> {
                    api.start(
                        StartTimerRequest(
                            mode = action.mode ?: TimerMode.Focus.raw,
                            durationSec = action.durationSec ?: 25 * 60L,
                            clientVersion = action.baseVersion
                        )
                    )
                }

                PendingActionType.Pause -> api.pause(VersionRequest(action.baseVersion))
                PendingActionType.Reset -> api.reset(VersionRequest(action.baseVersion))
                PendingActionType.Complete -> api.complete(VersionRequest(action.baseVersion))
            }
        } catch (_: IOException) {
            emptyErrorResponse()
        }
    }

    private suspend fun pullStateLocked(userId: String) {
        runCatching { api.getState() }
            .onSuccess { timerState ->
                timerStateDao.upsert(timerState.toEntity(lastSyncedAt = nowSec()))
            }
            .onFailure {
                val local = timerStateDao.getByUser(userId)
                if (local == null) {
                    timerStateDao.upsert(defaultState(userId, nowSec()).toEntity(lastSyncedAt = nowSec()))
                }
            }
    }

    private suspend fun pullHistoryLocked(userId: String) {
        val since = historyDao.newestCreatedAt(userId) ?: 0L
        runCatching { api.history(since = since) }
            .onSuccess { response ->
                if (response.items.isNotEmpty()) {
                    historyDao.upsertAll(response.items.map { it.toEntity() })
                }
            }
    }

    private fun parseConflict(errorBody: ResponseBody?): ApiErrorResponse? {
        val raw = runCatching { errorBody?.string() }.getOrNull() ?: return null
        return runCatching { errorAdapter.fromJson(raw) }.getOrNull()
    }

    private fun emptyErrorResponse(): Response<TimerStateDto> {
        return Response.error(
            503,
            """{"code":"network_error","message":"network unavailable"}"""
                .toResponseBody("application/json".toMediaTypeOrNull())
        )
    }

    private fun defaultState(userId: String, now: Long): TimerState {
        return TimerState(
            userId = userId,
            mode = TimerMode.Focus,
            status = TimerStatus.Idle,
            durationSec = 25 * 60,
            remainingSec = 25 * 60,
            startedAt = null,
            endAt = null,
            version = 1,
            updatedAt = now
        )
    }

    private fun nowSec(): Long = System.currentTimeMillis() / 1000
}
