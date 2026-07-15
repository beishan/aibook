package com.aibook.android.core.network.opds

import java.io.File

class OpdsCatalogService(
    private val transport: OpdsTransport,
    private val parser: OpdsFeedParser
) {
    fun load(connection: OpdsConnection, href: String? = null): OpdsFeed {
        val url = if (href.isNullOrBlank()) {
            connection.baseUrl
        } else {
            OpdsRequestFactory.resolveUrl(connection, href)
        }
        val xml = transport.get(url, OpdsRequestFactory.basicAuthHeader(connection))

        return parser.parse(xml)
    }

    fun download(connection: OpdsConnection, href: String): ByteArray {
        val url = OpdsRequestFactory.resolveUrl(connection, href)
        return transport.getBytes(url, OpdsRequestFactory.basicAuthHeader(connection))
    }

    fun download(
        connection: OpdsConnection,
        href: String,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
        isCancelled: () -> Boolean = { false }
    ): ByteArray {
        val url = OpdsRequestFactory.resolveUrl(connection, href)
        return transport.getBytes(url, OpdsRequestFactory.basicAuthHeader(connection), onProgress, isCancelled)
    }

    fun downloadTo(
        connection: OpdsConnection,
        href: String,
        destination: File,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
        isCancelled: () -> Boolean = { false }
    ): File {
        val url = OpdsRequestFactory.resolveUrl(connection, href)
        transport.downloadTo(url, OpdsRequestFactory.basicAuthHeader(connection), destination, onProgress, isCancelled)
        return destination
    }
}

interface OpdsTransport {
    fun get(url: String, authorizationHeader: String?): String

    fun getBytes(url: String, authorizationHeader: String?): ByteArray {
        return get(url, authorizationHeader).toByteArray(Charsets.UTF_8)
    }

    fun getBytes(
        url: String,
        authorizationHeader: String?,
        onProgress: (Long, Long?) -> Unit,
        isCancelled: () -> Boolean
    ): ByteArray {
        val bytes = getBytes(url, authorizationHeader)
        check(!isCancelled()) { "下载已取消" }
        onProgress(bytes.size.toLong(), bytes.size.toLong())
        return bytes
    }

    fun downloadTo(
        url: String,
        authorizationHeader: String?,
        destination: File,
        onProgress: (Long, Long?) -> Unit,
        isCancelled: () -> Boolean
    ) {
        destination.parentFile?.mkdirs()
        destination.writeBytes(getBytes(url, authorizationHeader, onProgress, isCancelled))
    }
}
