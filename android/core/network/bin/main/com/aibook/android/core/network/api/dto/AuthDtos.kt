package com.aibook.android.core.network.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    val nickname: String? = null
)

@Serializable
data class AuthResponse(
    val token: String,
    val type: String = "Bearer",
    val username: String? = null,
    val email: String? = null,
    val role: String? = null
)
