package com.aibook.android.core.network.api

import com.aibook.android.core.network.api.dto.CreateBookmarkRequest
import com.aibook.android.core.network.api.dto.CreateHighlightRequest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.util.concurrent.CountDownLatch
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class AnnotationApiContractTest {
    @Test
    fun `bookmark endpoints match backend contract`() = runSuspend {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(201).setBody(BOOKMARK_JSON))
        server.start()
        try {
            val api = ApiServiceFactory.createAnnotationApi(retrofit(server))
            val bookmark = api.createBookmark(
                7,
                CreateBookmarkRequest(
                    title = "第八章",
                    chapter = "第八章",
                    chapterIndex = 8,
                    cfi = "{\"lineIndex\":12}",
                    scrollPosition = 30
                )
            )
            val request = server.takeRequest()

            assertEquals("/api/books/7/bookmarks", request.path)
            assertContains(request.body.readUtf8(), "\"chapterIndex\":8")
            assertEquals(19L, bookmark.id)
        } finally {
            server.close()
        }
    }

    @Test
    fun `highlight endpoints match backend contract`() = runSuspend {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(201).setBody(HIGHLIGHT_JSON))
        server.start()
        try {
            val api = ApiServiceFactory.createAnnotationApi(retrofit(server))
            val highlight = api.createHighlight(
                7,
                CreateHighlightRequest(
                    cfiRange = "{\"lineIndex\":3}",
                    text = "被标记的文字",
                    color = "#FFE082",
                    chapter = "第八章",
                    note = "重点"
                )
            )
            val request = server.takeRequest()

            assertEquals("/api/books/7/highlights", request.path)
            assertContains(request.body.readUtf8(), "\"note\":\"重点\"")
            assertEquals("被标记的文字", highlight.text)
        } finally {
            server.close()
        }
    }

    private fun retrofit(server: MockWebServer) = ApiServiceFactory.createRetrofit(
        server.url("/").toString(),
        ApiServiceFactory.createOkHttpClient(object : AuthTokenProvider {
            override fun token() = "jwt"
        })
    )

    private fun <T> runSuspend(block: suspend () -> T): T {
        val latch = CountDownLatch(1)
        var outcome: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) {
                outcome = result
                latch.countDown()
            }
        })
        latch.await()
        return checkNotNull(outcome).getOrThrow()
    }

    private companion object {
        const val BOOKMARK_JSON = """{"id":19,"title":"第八章","chapter":"第八章","chapterIndex":8,"cfi":"{\"lineIndex\":12}","scrollPosition":30}"""
        const val HIGHLIGHT_JSON = """{"id":23,"cfiRange":"{\"lineIndex\":3}","text":"被标记的文字","color":"#FFE082","chapter":"第八章","note":"重点"}"""
    }
}
