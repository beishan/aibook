package com.aibook.android.core.network.api

import com.aibook.android.core.network.api.dto.BookListDTO
import com.aibook.android.core.network.api.dto.ReadingProgressDTO
import com.aibook.android.core.network.api.dto.SaveProgressRequest
import com.aibook.android.core.network.api.dto.ShelfOverviewDTO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerLibraryDtosTest {
    @Test
    fun `decodes backend shelf and book list payloads`() {
        val shelf = ApiServiceFactory.json.decodeFromString<ShelfOverviewDTO>(
            """{"ungroupedBooks":[{"id":7,"title":"三体"}],"groups":[],"totalBooks":1}"""
        )
        val bookList = ApiServiceFactory.json.decodeFromString<BookListDTO>(
            """{"id":3,"name":"科幻精选","description":"家庭书单","books":[{"id":7,"title":"三体"}]}"""
        )

        assertEquals(7L, shelf.ungroupedBooks.single().id)
        assertEquals("科幻精选", bookList.name)
        assertEquals("三体", bookList.books.single().title)
    }

    @Test
    fun `reading progress preserves chapter metadata`() {
        val response = ApiServiceFactory.json.decodeFromString<ReadingProgressDTO>(
            """{"bookId":7,"versionId":2,"currentChapter":"chapter-8","currentChapterTitle":"黑暗森林","chapterProgress":36,"totalProgress":58}"""
        )
        val requestJson = ApiServiceFactory.json.encodeToString(
            SaveProgressRequest(
                currentChapter = response.currentChapter,
                currentChapterTitle = response.currentChapterTitle,
                chapterProgress = response.chapterProgress,
                totalProgress = response.totalProgress
            )
        )

        assertEquals(2L, response.versionId)
        assertEquals("黑暗森林", response.currentChapterTitle)
        assertTrue(requestJson.contains("\"totalProgress\":58"))
        assertTrue(requestJson.contains("\"currentChapterTitle\":\"黑暗森林\""))
    }
}
