package com.example.pomodoro.data.remote

import com.example.pomodoro.data.repository.SecureTokenStorage
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val tokenStorage: SecureTokenStorage
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = tokenStorage.getSession()?.accessToken
        if (token.isNullOrBlank()) {
            return chain.proceed(original)
        }
        val request = original.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(request)
    }
}
