package com.aibook.android.core.network.opds

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OkHttpOpdsTransportResumeTest {
    @Test
    fun `sends supplied basic authorization header`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"metadata":{"title":"书库"}}"""))
        server.start()
        try {
            OkHttpOpdsTransport(OkHttpClient()).get(
                server.url("/opds/v2").toString(),
                "Basic cmVhZGVyOnNlY3JldA=="
            )

            assertEquals(
                "Basic cmVhZGVyOnNlY3JldA==",
                server.takeRequest().headers["Authorization"]
            )
        } finally {
            server.close()
        }
    }

    @Test
    fun `reports whether credentials were supplied when server returns 401`() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .addHeader("X-Aibook-Auth-Status", "invalid")
        )
        server.start()
        try {
            val error = assertFailsWith<OpdsAuthenticationException> {
                OkHttpOpdsTransport(OkHttpClient()).get(
                    server.url("/opds/v2").toString(),
                    "Basic cmVhZGVyOnNlY3JldA=="
                )
            }

            assertTrue(error.credentialsSupplied)
            assertContains(error.message.orEmpty(), "已收到登录信息")
        } finally {
            server.close()
        }
    }

    @Test
    fun `identifies proxy that removed authorization before backend`() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .addHeader("X-Aibook-Auth-Status", "missing")
        )
        server.start()
        try {
            val error = assertFailsWith<OpdsAuthenticationException> {
                OkHttpOpdsTransport(OkHttpClient()).get(
                    server.url("/opds/v2").toString(),
                    "Basic cmVhZGVyOnNlY3JldA=="
                )
            }

            assertContains(error.message.orEmpty(), "服务端未收到 Authorization")
        } finally {
            server.close()
        }
    }

    @Test
    fun `resumes partial file with range request`() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .addHeader("Content-Range", "bytes 5-10/11")
                .setBody(" world")
        )
        server.start()
        val file = File.createTempFile("opds-resume", ".part").apply { writeText("hello") }
        try {
            OkHttpOpdsTransport(OkHttpClient()).downloadTo(
                server.url("/book.epub").toString(), null, file, { _, _ -> }, { false }
            )

            assertEquals("bytes=5-", server.takeRequest().headers["Range"])
            assertEquals("hello world", file.readText())
        } finally {
            file.delete()
            server.close()
        }
    }
}
