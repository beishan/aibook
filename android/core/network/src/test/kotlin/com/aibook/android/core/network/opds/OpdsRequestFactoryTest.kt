package com.aibook.android.core.network.opds

import kotlin.test.Test
import kotlin.test.assertEquals

class OpdsRequestFactoryTest {
    @Test
    fun `creates basic auth header when credentials exist`() {
        val connection = OpdsConnection(
            id = "home",
            name = "家庭书库",
            baseUrl = "http://192.168.1.100:8080/opds/",
            username = "reader",
            password = "secret"
        )

        assertEquals("Basic cmVhZGVyOnNlY3JldA==", OpdsRequestFactory.basicAuthHeader(connection))
    }

    @Test
    fun `omits auth header when credentials are blank`() {
        val connection = OpdsConnection(
            id = "home",
            name = "家庭书库",
            baseUrl = "http://192.168.1.100:8080/opds/"
        )

        assertEquals(null, OpdsRequestFactory.basicAuthHeader(connection))
    }

    @Test
    fun `resolves relative opds links against base url`() {
        val connection = OpdsConnection(
            id = "home",
            name = "家庭书库",
            baseUrl = "http://192.168.1.100:8080/opds/"
        )

        assertEquals(
            "http://192.168.1.100:8080/opds/books/1/download",
            OpdsRequestFactory.resolveUrl(connection, "books/1/download")
        )
        assertEquals(
            "http://192.168.1.100:8080/opds/books/2/download",
            OpdsRequestFactory.resolveUrl(connection, "/opds/books/2/download")
        )
    }
}
