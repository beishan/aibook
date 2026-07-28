package com.aibook.android.core.network.opds

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
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
            checkSuccessful(response, authorizationHeader)
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
            checkSuccessful(response, authorizationHeader)
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
            checkSuccessful(response, authorizationHeader)

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

    private fun checkSuccessful(response: Response, authorizationHeader: String?) {
        if (response.isSuccessful) return
        if (response.code == 401) {
            val finalRequestHasCredentials = !response.request.header("Authorization").isNullOrBlank()
            val serverAuthStatus = response.header(AUTH_STATUS_HEADER)
            val message = when {
                authorizationHeader.isNullOrBlank() ->
                    "OPDS 需要登录，请填写用户名和密码"
                !finalRequestHasCredentials ->
                    "OPDS 地址发生重定向并移除了登录信息，请直接使用最终地址：${response.request.url}"
                serverAuthStatus == "missing" ->
                    "OPDS 服务端未收到 Authorization，请重新部署或检查反向代理配置"
                serverAuthStatus == "invalid" ->
                    "OPDS 服务端已收到登录信息，但用户名或密码校验失败"
                else ->
                    "OPDS 登录失败：服务端未提供鉴权诊断，请确认已重新部署最新版 backend 和 frontend"
            }
            throw OpdsAuthenticationException(message, credentialsSupplied = !authorizationHeader.isNullOrBlank())
        }
        throw OpdsNetworkException("OPDS request failed: HTTP ${response.code}")
    }

    private companion object {
        const val AUTH_STATUS_HEADER = "X-Aibook-Auth-Status"
    }
}

open class OpdsNetworkException(message: String) : RuntimeException(message)

class OpdsAuthenticationException(
    message: String,
    val credentialsSupplied: Boolean
) : OpdsNetworkException(message)
