package com.aibook.android.core.network.api

import com.aibook.android.core.network.api.dto.AuthResponse
import com.aibook.android.core.network.api.dto.LoginRequest
import com.aibook.android.core.network.api.dto.RegisterRequest
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse
}
