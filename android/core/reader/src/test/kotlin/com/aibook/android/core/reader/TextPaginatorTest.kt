package com.aibook.android.core.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class TextPaginatorTest {
    @Test
    fun `paginates text by maximum characters`() {
        val pages = TextPaginator.paginate("1234567890abcdef", maxCharsPerPage = 5)

        assertEquals(4, pages.size)
        assertEquals("12345", pages[0].text)
        assertEquals("67890", pages[1].text)
        assertEquals("abcde", pages[2].text)
        assertEquals("f", pages[3].text)
        assertEquals(0.5f, pages[2].progress)
    }

    @Test
    fun `returns empty page for blank content`() {
        val pages = TextPaginator.paginate("", maxCharsPerPage = 500)

        assertEquals(1, pages.size)
        assertEquals("", pages.single().text)
        assertEquals(0f, pages.single().progress)
    }
}
