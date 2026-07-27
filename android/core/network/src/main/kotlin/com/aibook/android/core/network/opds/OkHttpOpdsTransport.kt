package com.aibook.android.core.network.opds

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File

class OkHttpOpdsTransport(
    private val client: OkHttpClient = OkHttpClient()
) : OpdsTransport {
    override fun get(url: String, authorizationHeader: String?): String {
        return execute(url, authorizationHeader)
    }

    override fun getBytes(url: String, authorizationHeader: String?): ByteArray {
        return executeBytes(url, authorizationHeader)
    }

    override fun getBytes(
        url: String,
        authorizationHeader: String?,
        onProgress: (Long, Long?) -> Unit,
        isCancelled: () -> Boolean
    ): ByteArray {
        val request = request(url, authorizationHeader)
        client.newCall(request).execute().use { response ->
            checkSuccessful(response.code, authorizationHeader)
            val body = response.body
            val total = body.contentLength().takeIf { it >= 0 }
            val output = ByteArrayOutputStream(total?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 32 * 1024)
            body.byteStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var downloaded = 0L
                while (true) {
                    check(!isCancelled()) { "下载已取消" }
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    downloaded += count
                    onProgress(downloaded, total)
                }
            }
            return output.toByteArray()
        }
    }

    override fun downloadTo(
        url: String,
        authorizationHeader: String?,
        destination: File,
        onProgress: (Long, Long?) -> Unit,
        isCancelled: () -> Boolean
    ) {
        destination.parentFile?.mkdirs()
        val existing = destination.length().takeIf { destination.exists() } ?: 0L
        val builder = requestBuilder(url, authorizationHeader)
        if (existing > 0) builder.header("Range", "bytes=$existing-")
        client.newCall(builder.build()).execute().use { response ->
            checkSuccessful(response.code, authorizationHeader)
            val resumed = existing > 0 && response.code == 206
            val start = if (resumed) existing else 0L
            val bodyLength = response.body.contentLength().takeIf { it >= 0 }
            val total = bodyLength?.plus(start)
            java.io.FileOutputStream(destination, resumed).buffered().use { output ->
                response.body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = start
                    while (true) {
                        check(!isCancelled()) { "下载已取消" }
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        onProgress(downloaded, total)
                    }
                }
            }
        }
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
            checkSuccessful(response.code, authorizationHeader)

            return response.body.bytes()
        }
    }

    private fun request(url: String, authorizationHeader: String?): Request = Request.Builder()
        .url(url)
        .header("Accept", "application/atom+xml, application/opds+json, application/xml;q=0.9, */*;q=0.5")
        .apply { if (!authorizationHeader.isNullOrBlank()) header("Authorization", authorizationHeader) }
        .build()

    private fun requestBuilder(url: String, authorizationHeader: String?): Request.Builder = Request.Builder()
        .url(url)
        .header("Accept", "application/atom+xml, application/opds+json, application/xml;q=0.9, */*;q=0.5")
        .apply { if (!authorizationHeader.isNullOrBlank()) header("Authorization", authorizationHeader) }

    private fun checkSuccessful(code: Int, authorizationHeader: String?) {
        if (code in 200..299) return
        if (code == 401) {
            val message = if (authorizationHeader.isNullOrBlank()) {
                "OPDS 需要登录，请填写用户名和密码"
            } else {
                "OPDS 登录失败：用户名或密码错误，或反向代理未转发 Authorization"
            }
            throw OpdsAuthenticationException(message, credentialsSupplied = !authorizationHeader.isNullOrBlank())
        }
        throw OpdsNetworkException("OPDS request failed: HTTP $code")
    }
}

open class OpdsNetworkException(message: String) : RuntimeException(message)

class OpdsAuthenticationException(
    message: String,
    val credentialsSupplied: Boolean
) : OpdsNetworkException(message)
