package com.example.pomodoro.data.repository

import com.example.pomodoro.data.remote.PomodoroApi
import com.example.pomodoro.data.remote.dto.AuthRequest
import com.example.pomodoro.data.remote.dto.LogoutRequest
import kotlinx.coroutines.flow.StateFlow

class AuthRepository(
    private val api: PomodoroApi,
    private val tokenStorage: SecureTokenStorage
) {
    fun observeSession(): StateFlow<AuthSession?> = tokenStorage.observeSession()

    fun currentSession(): AuthSession? = tokenStorage.getSession()

    suspend fun register(email: String, password: String): Result<AuthSession> {
        return runCatching {
            val response = api.register(AuthRequest(email, password))
            AuthSession(
                userId = response.userId,
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
                expiresInSec = response.expiresInSec
            ).also(tokenStorage::saveSession)
        }
    }

    suspend fun login(email: String, password: String): Result<AuthSession> {
        return runCatching {
            val response = api.login(AuthRequest(email, password))
            AuthSession(
                userId = response.userId,
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
                expiresInSec = response.expiresInSec
            ).also(tokenStorage::saveSession)
        }
    }

    suspend fun logout() {
        val refreshToken = tokenStorage.getSession()?.refreshToken
        if (!refreshToken.isNullOrBlank()) {
            runCatching { api.logout(LogoutRequest(refreshToken)) }
        }
        tokenStorage.clear()
    }

    fun clearSession() {
        tokenStorage.clear()
    }
}
