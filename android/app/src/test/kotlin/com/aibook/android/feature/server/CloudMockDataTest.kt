package com.aibook.android.feature.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CloudMockDataTest {
    @Test
    fun `mock catalog exposes books favorites shelf and lists`() {
        assertTrue(CloudMockData.books.size >= 6)
        assertTrue(CloudMockData.favorites().isNotEmpty())
        assertTrue(CloudMockData.shelfBookIds.all { id -> CloudMockData.book(id) != null })
        assertTrue(CloudMockData.bookLists.all { it.books.isNotEmpty() })
    }

    @Test
    fun `search matches title author category and tag`() {
        assertEquals(listOf("三体"), CloudMockData.search("三体").map { it.title })
        assertEquals(setOf("三体", "球状闪电"), CloudMockData.search("刘慈欣").map { it.title }.toSet())
        assertTrue(CloudMockData.search("文学").size >= 3)
        assertEquals(listOf("小王子"), CloudMockData.search("治愈").map { it.title })
    }

    @Test
    fun `detail fixtures include progress and valid booklist references`() {
        val book = assertNotNull(CloudMockData.book(9001))
        assertEquals("三体", book.title)
        assertEquals(37, CloudMockData.progress(9001).totalProgress)
        assertNotNull(CloudMockData.bookList(9101))
    }
}
