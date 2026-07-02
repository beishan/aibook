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
}

fun interface OpdsTransport {
    fun get(url: String, authorizationHeader: String?): String
}
