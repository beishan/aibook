package com.aibook.android.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class PdfReadingPositionTest {

    @Test
    fun lastPageIsComplete() {
        assertEquals(1f, PdfReadingPosition.progress(pageIndex = 9, pageCount = 10))
    }

    @Test
    fun singlePageStartsAtZero() {
        assertEquals(0f, PdfReadingPosition.progress(pageIndex = 0, pageCount = 1))
    }

    @Test
    fun restoredPageIsClamped() {
        assertEquals(4, PdfReadingPosition.clampPage(pageIndex = 99, pageCount = 5))
        assertEquals(0, PdfReadingPosition.clampPage(pageIndex = -3, pageCount = 5))
    }
}
