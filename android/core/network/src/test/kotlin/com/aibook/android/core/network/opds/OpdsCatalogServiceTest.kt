package com.aibook.android.core.network.opds

import kotlin.test.Test
import kotlin.test.assertEquals

class OpdsCatalogServiceTest {
    @Test
    fun `loads root feed with resolved url and basic auth`() {
        val transport = RecordingTransport(
            response = """
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <title>家庭书库</title>
                </feed>
            """.trimIndent()
        )
        val service = OpdsCatalogService(transport, OpdsFeedParser())
        val connection = OpdsConnection(
            id = "home",
            name = "家庭书库",
            baseUrl = "http://192.168.1.100:8080/opds/",
            username = "reader",
            password = "secret"
        )

        val feed = service.load(connection)

        assertEquals("家庭书库", feed.title)
        assertEquals("http://192.168.1.100:8080/opds/", transport.requestedUrl)
        assertEquals("Basic cmVhZGVyOnNlY3JldA==", transport.requestedAuthHeader)
    }

    @Test
    fun `loads child feed from relative href`() {
        val transport = RecordingTransport(
            response = """
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <title>科幻</title>
                </feed>
            """.trimIndent()
        )
        val service = OpdsCatalogService(transport, OpdsFeedParser())
        val connection = OpdsConnection(
            id = "home",
            name = "家庭书库",
            baseUrl = "http://192.168.1.100:8080/opds/"
        )

        val feed = service.load(connection, "catalog/scifi")

        assertEquals("科幻", feed.title)
        assertEquals("http://192.168.1.100:8080/opds/catalog/scifi", transport.requestedUrl)
    }

    @Test
    fun `downloads acquisition bytes with basic auth`() {
        val transport = RecordingTransport(response = "", bytes = byteArrayOf(1, 2, 3))
        val service = OpdsCatalogService(transport, OpdsFeedParser())
        val connection = OpdsConnection(
            id = "home",
            name = "家庭书库",
            baseUrl = "http://192.168.1.100:8080/opds/",
            username = "reader",
            password = "secret"
        )

        val bytes = service.download(connection, "books/1/download")

        assertEquals(listOf<Byte>(1, 2, 3), bytes.toList())
        assertEquals("http://192.168.1.100:8080/opds/books/1/download", transport.requestedUrl)
        assertEquals("Basic cmVhZGVyOnNlY3JldA==", transport.requestedAuthHeader)
    }

    private class RecordingTransport(
        private val response: String,
        private val bytes: ByteArray = response.toByteArray()
    ) : OpdsTransport {
        var requestedUrl: String? = null
        var requestedAuthHeader: String? = null

        override fun get(url: String, authorizationHeader: String?): String {
            requestedUrl = url
            requestedAuthHeader = authorizationHeader
            return response
        }

        override fun getBytes(url: String, authorizationHeader: String?): ByteArray {
            requestedUrl = url
            requestedAuthHeader = authorizationHeader
            return bytes
        }
    }
}
