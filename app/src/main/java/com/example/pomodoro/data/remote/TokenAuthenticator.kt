package com.example.pomodoro.data.remote

import com.example.pomodoro.data.remote.dto.RefreshRequest
import com.example.pomodoro.data.repository.SecureTokenStorage
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(
    private val tokenStorage: SecureTokenStorage,
    private val refreshApi: PomodoroApi
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.url.encodedPath.contains("/auth/refresh")) {
            return null
        }
        if (responseCount(response) >= 2) {
            return null
        }
        val current = tokenStorage.getSession() ?: return null
        val refreshed = runBlocking {
            runCatching { refreshApi.refresh(RefreshRequest(current.refreshToken)) }.getOrNull()
        } ?: return null
        tokenStorage.updateAccessAndRefresh(
            accessToken = refreshed.accessToken,
            refreshToken = refreshed.refreshToken,
            expiresInSec = refreshed.expiresInSec
        )
        return response.request.newBuilder()
            .header("Authorization", "Bearer ${refreshed.accessToken}")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var current: Response? = response
        var result = 1
        while (current?.priorResponse != null) {
            result++
            current = current.priorResponse
        }
        return result
    }
}
