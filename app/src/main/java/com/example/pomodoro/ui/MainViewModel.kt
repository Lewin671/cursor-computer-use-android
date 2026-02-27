package com.example.pomodoro.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pomodoro.ServiceLocator
import com.example.pomodoro.data.repository.AuthSession
import com.example.pomodoro.domain.FocusHistory
import com.example.pomodoro.domain.TimerMode
import com.example.pomodoro.domain.TimerState
import com.example.pomodoro.domain.TimerStatus
import com.example.pomodoro.service.TimerForegroundService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val isRegisterMode: Boolean = false,
    val loading: Boolean = false,
    val errorMessage: String? = null
)

data class TimerUiState(
    val state: TimerState? = null,
    val history: List<FocusHistory> = emptyList(),
    val selectedMode: TimerMode = TimerMode.Focus,
    val customDurationSec: Long = 25 * 60,
    val syncing: Boolean = false,
    val errorMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val locator = ServiceLocator.get(application)
    private val authRepository = locator.authRepository
    private val timerRepository = locator.timerRepository

    private val authStateMutable = MutableStateFlow(AuthUiState())
    val authUiState: StateFlow<AuthUiState> = authStateMutable

    private val ticker = MutableStateFlow(System.currentTimeMillis() / 1000)
    private val timerStateMutable = MutableStateFlow(TimerUiState())
    val timerUiState: StateFlow<TimerUiState> = timerStateMutable

    val session: StateFlow<AuthSession?> = authRepository.observeSession()

    private val userIdFlow = session.map { it?.userId }

    private val rawTimerState = userIdFlow.flatMapLatest { userId ->
        if (userId.isNullOrBlank()) {
            MutableStateFlow<TimerState?>(null)
        } else {
            timerRepository.observeTimerState(userId)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val historyFlow = userIdFlow.flatMapLatest { userId ->
        if (userId.isNullOrBlank()) {
            MutableStateFlow(emptyList<FocusHistory>())
        } else {
            timerRepository.observeHistory(userId)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var timerServiceJob: Job? = null

    init {
        viewModelScope.launch {
            while (true) {
                ticker.value = System.currentTimeMillis() / 1000
                delay(1_000)
            }
        }

        viewModelScope.launch {
            combine(rawTimerState, historyFlow, ticker) { state, history, now ->
                val resolved = state?.let { current ->
                    if (current.status == TimerStatus.Running && current.endAt != null) {
                        current.copy(remainingSec = max(0, current.endAt - now))
                    } else {
                        current
                    }
                }
                resolved to history
            }.collect { (state, history) ->
                timerStateMutable.update {
                    it.copy(
                        state = state,
                        history = history
                    )
                }
            }
        }

        viewModelScope.launch {
            session.filterNotNull().collect {
                syncNow()
            }
        }

        viewModelScope.launch {
            rawTimerState
                .distinctUntilChangedBy { state -> "${state?.status}-${state?.endAt}-${state?.version}" }
                .collect { state ->
                    manageForegroundTimer(state)
                }
        }
    }

    fun updateEmail(value: String) {
        authStateMutable.update { it.copy(email = value) }
    }

    fun updatePassword(value: String) {
        authStateMutable.update { it.copy(password = value) }
    }

    fun toggleAuthMode() {
        authStateMutable.update {
            it.copy(
                isRegisterMode = !it.isRegisterMode,
                errorMessage = null
            )
        }
    }

    fun loginOrRegister() {
        val form = authStateMutable.value
        if (form.loading) return
        viewModelScope.launch {
            authStateMutable.update { it.copy(loading = true, errorMessage = null) }
            val result = if (form.isRegisterMode) {
                authRepository.register(form.email.trim(), form.password)
            } else {
                authRepository.login(form.email.trim(), form.password)
            }
            result.onFailure { throwable ->
                authStateMutable.update {
                    it.copy(
                        loading = false,
                        errorMessage = throwable.message ?: "request failed"
                    )
                }
            }.onSuccess {
                authStateMutable.update { state ->
                    state.copy(
                        loading = false,
                        password = "",
                        errorMessage = null
                    )
                }
                syncNow()
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            timerStateMutable.value = TimerUiState()
            TimerForegroundService.stop(getApplication())
        }
    }

    fun updateSelectedMode(mode: TimerMode) {
        timerStateMutable.update { it.copy(selectedMode = mode) }
    }

    fun updateCustomDuration(minutes: String) {
        val min = minutes.toLongOrNull() ?: return
        if (min in 1..240) {
            timerStateMutable.update { it.copy(customDurationSec = min * 60) }
        }
    }

    fun startTimer() {
        val userId = session.value?.userId ?: return
        val ui = timerStateMutable.value
        viewModelScope.launch {
            timerStateMutable.update { it.copy(syncing = true, errorMessage = null) }
            runCatching {
                timerRepository.start(userId, ui.selectedMode, ui.customDurationSec)
            }.onFailure {
                timerStateMutable.update { state ->
                    state.copy(
                        errorMessage = it.message ?: "start failed"
                    )
                }
            }
            timerStateMutable.update { it.copy(syncing = false) }
            syncNow()
        }
    }

    fun pauseTimer() {
        val userId = session.value?.userId ?: return
        viewModelScope.launch {
            timerStateMutable.update { it.copy(syncing = true, errorMessage = null) }
            runCatching { timerRepository.pause(userId) }
                .onFailure {
                    timerStateMutable.update { state ->
                        state.copy(errorMessage = it.message ?: "pause failed")
                    }
                }
            timerStateMutable.update { it.copy(syncing = false) }
            syncNow()
        }
    }

    fun resetTimer() {
        val userId = session.value?.userId ?: return
        viewModelScope.launch {
            timerStateMutable.update { it.copy(syncing = true, errorMessage = null) }
            runCatching { timerRepository.reset(userId) }
                .onFailure {
                    timerStateMutable.update { state ->
                        state.copy(errorMessage = it.message ?: "reset failed")
                    }
                }
            timerStateMutable.update { it.copy(syncing = false) }
            syncNow()
        }
    }

    fun completeTimerIfNeeded() {
        val userId = session.value?.userId ?: return
        viewModelScope.launch {
            runCatching {
                timerRepository.completeIfExpired(userId)
                timerRepository.syncNow(userId)
            }
        }
    }

    fun syncNow() {
        val userId = session.value?.userId ?: return
        viewModelScope.launch {
            timerStateMutable.update { it.copy(syncing = true) }
            runCatching { timerRepository.syncNow(userId) }
                .onFailure {
                    timerStateMutable.update { state ->
                        state.copy(errorMessage = it.message ?: "sync failed")
                    }
                }
            timerStateMutable.update { it.copy(syncing = false) }
        }
    }

    private fun manageForegroundTimer(state: TimerState?) {
        timerServiceJob?.cancel()
        val userId = session.value?.userId
        if (userId != null && state?.status == TimerStatus.Running) {
            TimerForegroundService.start(getApplication(), userId)
            timerServiceJob = viewModelScope.launch {
                while (true) {
                    timerRepository.completeIfExpired(userId)
                    delay(1_000)
                }
            }
        } else {
            TimerForegroundService.stop(getApplication())
        }
    }
}
