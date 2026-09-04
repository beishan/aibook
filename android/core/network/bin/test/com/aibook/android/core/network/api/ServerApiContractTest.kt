package com.aibook.android.core.network.api

import com.aibook.android.core.network.api.dto.SaveProgressRequest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.util.concurrent.CountDownLatch
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerApiContractTest {
    @Test
    fun `authenticated shelf response matches backend contract`() = runSuspend {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(SHELF_JSON))
        server.start()
        try {
            val api = ApiServiceFactory.createServerLibraryApi(retrofit(server, "test-jwt"))

            val shelf = api.getShelf()
            val request = server.takeRequest()

            assertEquals("Bearer test-jwt", request.headers["Authorization"])
            assertEquals("/api/shelf", request.path)
            assertEquals(1, shelf.totalBooks)
            assertEquals("三体", shelf.ungroupedBooks.single().title)
        } finally {
            server.close()
        }
    }

    @Test
    fun `book list response ignores entity fields and retains books`() = runSuspend {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(BOOK_LIST_JSON))
        server.start()
        try {
            val lists = ApiServiceFactory.createServerLibraryApi(retrofit(server, null)).getBookLists()

            assertEquals("科幻必读", lists.single().name)
            assertEquals(7L, lists.single().books.single().id)
        } finally {
            server.close()
        }
    }

    @Test
    fun `reading progress sends backend field names`() = runSuspend {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(PROGRESS_JSON))
        server.start()
        try {
            val api = ApiServiceFactory.createReadingProgressApi(retrofit(server, "jwt"))

            val saved = api.saveProgress(
                bookId = 7,
                versionId = null,
                request = SaveProgressRequest(
                    currentChapter = "chapter-18",
                    currentChapterTitle = "第 18 章",
                    chapterProgress = 42,
                    totalProgress = 37
                )
            )
            val request = server.takeRequest()
            val body = request.body.readUtf8()

            assertEquals("/api/reading-progress/book/7", request.path)
            assertContains(body, "\"currentChapter\":\"chapter-18\"")
            assertContains(body, "\"totalProgress\":37")
            assertEquals(37, saved.totalProgress)
        } finally {
            server.close()
        }
    }

    @Test
    fun `record open uses backend route`() = runSuspend {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(204))
        server.start()
        try {
            ApiServiceFactory.createBookApi(retrofit(server, "jwt")).recordBookOpen(7)

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/books/7/open", request.path)
            assertTrue(request.bodySize == 0L)
        } finally {
            server.close()
        }
    }

    private fun retrofit(server: MockWebServer, token: String?) = ApiServiceFactory.createRetrofit(
        server.url("/").toString(),
        ApiServiceFactory.createOkHttpClient(object : AuthTokenProvider {
            override fun token() = token
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
        const val SHELF_JSON = """{"ungroupedBooks":[{"id":7,"title":"三体","format":"EPUB","onShelf":true}],"groups":[],"totalBooks":1}"""
        const val BOOK_LIST_JSON = """[{"id":3,"name":"科幻必读","description":"经典","sortOrder":0,"user":{"id":1},"books":[{"id":7,"title":"三体"}]}]"""
        const val PROGRESS_JSON = """{"id":9,"bookId":7,"currentChapter":"chapter-18","currentChapterTitle":"第 18 章","chapterProgress":42,"totalProgress":37,"readingTimeSeconds":60}"""
    }
}
