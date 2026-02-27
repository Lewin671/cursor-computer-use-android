package com.example.pomodoro.data.remote

import com.example.pomodoro.data.remote.dto.AuthRequest
import com.example.pomodoro.data.remote.dto.AuthResponse
import com.example.pomodoro.data.remote.dto.HistoryResponse
import com.example.pomodoro.data.remote.dto.LogoutRequest
import com.example.pomodoro.data.remote.dto.RefreshRequest
import com.example.pomodoro.data.remote.dto.StartTimerRequest
import com.example.pomodoro.data.remote.dto.TimerStateDto
import com.example.pomodoro.data.remote.dto.VersionRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface PomodoroApi {
    @POST("auth/register")
    suspend fun register(@Body request: AuthRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body request: AuthRequest): AuthResponse

    @POST("auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): AuthResponse

    @POST("auth/logout")
    suspend fun logout(@Body request: LogoutRequest): Response<Unit>

    @GET("timer/state")
    suspend fun getState(): TimerStateDto

    @POST("timer/start")
    suspend fun start(@Body request: StartTimerRequest): Response<TimerStateDto>

    @POST("timer/pause")
    suspend fun pause(@Body request: VersionRequest): Response<TimerStateDto>

    @POST("timer/reset")
    suspend fun reset(@Body request: VersionRequest): Response<TimerStateDto>

    @POST("timer/complete")
    suspend fun complete(@Body request: VersionRequest): Response<TimerStateDto>

    @GET("timer/history")
    suspend fun history(
        @Query("since") since: Long,
        @Query("limit") limit: Int = 100
    ): HistoryResponse
}
