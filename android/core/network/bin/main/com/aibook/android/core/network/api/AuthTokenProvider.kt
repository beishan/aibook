package com.aibook.android.core.network.api

fun interface AuthTokenProvider {
    fun token(): String?
}
