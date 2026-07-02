package com.aibook.android.core.network.opds

import okhttp3.OkHttpClient
import okhttp3.Request

class OkHttpOpdsTransport(
    private val client: OkHttpClient = OkHttpClient()
) : OpdsTransport {
    override fun get(url: String, authorizationHeader: String?): String {
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

            return response.body.string()
        }
    }
}

class OpdsNetworkException(message: String) : RuntimeException(message)
