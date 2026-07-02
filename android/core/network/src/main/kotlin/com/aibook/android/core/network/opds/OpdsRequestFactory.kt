package com.aibook.android.core.network.opds

import java.net.URI
import java.util.Base64

object OpdsRequestFactory {
    fun basicAuthHeader(connection: OpdsConnection): String? {
        val username = connection.username?.takeIf { it.isNotBlank() } ?: return null
        val password = connection.password ?: return null
        val token = Base64.getEncoder().encodeToString("$username:$password".toByteArray(Charsets.UTF_8))

        return "Basic $token"
    }

    fun resolveUrl(connection: OpdsConnection, href: String): String {
        val base = URI(connection.baseUrl.ensureTrailingSlash())
        return base.resolve(href).toString()
    }

    private fun String.ensureTrailingSlash(): String {
        return if (endsWith('/')) this else "$this/"
    }
}
