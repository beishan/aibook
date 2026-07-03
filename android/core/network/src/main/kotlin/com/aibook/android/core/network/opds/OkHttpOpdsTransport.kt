package com.aibook.android.core.network.opds

import okhttp3.OkHttpClient
import okhttp3.Request

class OkHttpOpdsTransport(
    private val client: OkHttpClient = OkHttpClient()
) : OpdsTransport {
    override fun get(url: String, authorizationHeader: String?): String {
        return execute(url, authorizationHeader)
    }

    override fun getBytes(url: String, authorizationHeader: String?): ByteArray {
        return executeBytes(url, authorizationHeader)
    }

    private fun execute(url: String, authorizationHeader: String?): String {
        return executeBytes(url, authorizationHeader).toString(Charsets.UTF_8)
    }

    private fun executeBytes(url: String, authorizationHeader: String?): ByteArray {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/atom+xml, application/opds+json, application/xml;q=0.9, */*;q=0.5")

        if (!authorizationHeader.isNullOrBlank()) {
            requestBuilder.header("Authorization", authorizationHeader)
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw OpdsNetworkException("OPDS request failed: HTTP ${response.code}")
            }

            return response.body.bytes()
        }
    }
}

class OpdsNetworkException(message: String) : RuntimeException(message)
