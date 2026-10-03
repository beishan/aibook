package com.aibook.android.core.network.api

fun interface AuthTokenProvider {
    fun token(): String?

    fun refreshToken(expiredToken: String): String? =
        token()?.takeIf { it != expiredToken }
}
