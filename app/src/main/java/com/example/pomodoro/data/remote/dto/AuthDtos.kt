package com.example.pomodoro.data.remote.dto

data class AuthRequest(
    val email: String,
    val password: String
)

data class AuthResponse(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresInSec: Long
)

data class RefreshRequest(
    val refreshToken: String
)

data class LogoutRequest(
    val refreshToken: String
)
