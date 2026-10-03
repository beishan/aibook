package com.aibook.android.core.network.api

import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class AuthInterceptor(
    private val tokenProvider: AuthTokenProvider
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider.token()
        val request = if (token.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        return chain.proceed(request)
    }
}

class AuthAuthenticator(
    private val tokenProvider: AuthTokenProvider
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.previousUnauthorizedCount() > 0) return null

        val path = response.request.url.encodedPath
        if (path.endsWith("/api/auth/login") || path.endsWith("/api/auth/register")) {
            return null
        }

        val failedToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ")
            ?.takeIf(String::isNotBlank)
            ?: return null
        val refreshedToken = tokenProvider.refreshToken(failedToken)
            ?.takeIf { it.isNotBlank() && it != failedToken }
            ?: return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer $refreshedToken")
            .build()
    }

    private fun Response.previousUnauthorizedCount(): Int {
        var count = 0
        var prior = priorResponse
        while (prior != null) {
            if (prior.code == 401) count += 1
            prior = prior.priorResponse
        }
        return count
    }
}
