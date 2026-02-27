package com.example.pomodoro.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AuthSession(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresInSec: Long
)

class SecureTokenStorage(context: Context) {
    private val prefs: SharedPreferences
    private val state = MutableStateFlow<AuthSession?>(null)

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            context,
            "auth_secure_store",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        state.value = readSession()
    }

    fun observeSession(): StateFlow<AuthSession?> = state.asStateFlow()

    fun getSession(): AuthSession? = state.value

    fun saveSession(session: AuthSession) {
        prefs.edit()
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_ACCESS, session.accessToken)
            .putString(KEY_REFRESH, session.refreshToken)
            .putLong(KEY_EXPIRES_IN, session.expiresInSec)
            .apply()
        state.value = session
    }

    fun updateAccessAndRefresh(accessToken: String, refreshToken: String, expiresInSec: Long) {
        val current = state.value ?: return
        saveSession(
            current.copy(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresInSec = expiresInSec
            )
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
        state.value = null
    }

    private fun readSession(): AuthSession? {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        val expiresIn = prefs.getLong(KEY_EXPIRES_IN, 0)
        return AuthSession(
            userId = userId,
            accessToken = access,
            refreshToken = refresh,
            expiresInSec = expiresIn
        )
    }

    private companion object {
        const val KEY_USER_ID = "user_id"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRES_IN = "expires_in_sec"
    }
}
