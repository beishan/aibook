package com.aibook.android.core.network.opds

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class OkHttpOpdsTransportResumeTest {
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
