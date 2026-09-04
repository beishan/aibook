package com.aibook.android.feature.shelf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookDetailPreviewRouteTest {
    @Test
    fun `debug preview route resolves to first local book`() {
        assertEquals(
            "local-book-1",
            resolveLocalPreviewBookId(DEBUG_FIRST_LOCAL_BOOK_ID, "local-book-1", debugBuild = true)
        )
    }

    @Test
    fun `debug preview route waits when shelf is empty`() {
        assertNull(resolveLocalPreviewBookId(DEBUG_FIRST_LOCAL_BOOK_ID, null, debugBuild = true))
    }

    @Test
    fun `release build never resolves preview route`() {
        assertEquals(
            DEBUG_FIRST_LOCAL_BOOK_ID,
            resolveLocalPreviewBookId(DEBUG_FIRST_LOCAL_BOOK_ID, "local-book-1", debugBuild = false)
        )
    }

    @Test
    fun `normal route is unchanged`() {
        assertEquals("book-42", resolveLocalPreviewBookId("book-42", "local-book-1", debugBuild = true))
    }
}
