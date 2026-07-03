package com.aibook.android.core.network.opds

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
}

interface OpdsTransport {
    fun get(url: String, authorizationHeader: String?): String

    fun getBytes(url: String, authorizationHeader: String?): ByteArray {
        return get(url, authorizationHeader).toByteArray(Charsets.UTF_8)
    }
}
