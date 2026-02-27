package com.example.pomodoro

import android.content.Context
import com.example.pomodoro.data.local.AppDatabase
import com.example.pomodoro.data.remote.AuthInterceptor
import com.example.pomodoro.data.remote.PomodoroApi
import com.example.pomodoro.data.remote.TokenAuthenticator
import com.example.pomodoro.data.repository.AuthRepository
import com.example.pomodoro.data.repository.SecureTokenStorage
import com.example.pomodoro.data.repository.TimerRepository
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class ServiceLocator private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val moshi: Moshi = Moshi.Builder().build()
    private val tokenStorage = SecureTokenStorage(appContext)
    private val db = AppDatabase.getInstance(appContext)

    private val refreshApi: PomodoroApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(baseClient())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(PomodoroApi::class.java)
    }

    private val authorizedApi: PomodoroApi by lazy {
        val client = baseClient().newBuilder()
            .addInterceptor(AuthInterceptor(tokenStorage))
            .authenticator(TokenAuthenticator(tokenStorage, refreshApi))
            .build()
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(PomodoroApi::class.java)
    }

    val authRepository: AuthRepository by lazy {
        AuthRepository(api = refreshApi, tokenStorage = tokenStorage)
    }

    val timerRepository: TimerRepository by lazy {
        TimerRepository(
            api = authorizedApi,
            timerStateDao = db.timerStateDao(),
            historyDao = db.focusHistoryDao(),
            pendingActionDao = db.pendingActionDao(),
            moshi = moshi
        )
    }

    private fun baseClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    companion object {
        @Volatile
        private var INSTANCE: ServiceLocator? = null

        fun get(context: Context): ServiceLocator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ServiceLocator(context).also { INSTANCE = it }
            }
        }
    }
}
