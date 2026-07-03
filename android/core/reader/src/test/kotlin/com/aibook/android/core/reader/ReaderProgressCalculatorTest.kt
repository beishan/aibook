package com.aibook.android.core.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderProgressCalculatorTest {

    @Test
    fun chapterProgressUsesChapterPosition() {
        assertEquals(0.5f, ReaderProgressCalculator.chapterProgress(chapterIndex = 2, totalChapters = 4))
    }

    @Test
    fun chapterProgressClampsInvalidValues() {
        assertEquals(0f, ReaderProgressCalculator.chapterProgress(chapterIndex = -1, totalChapters = 4))
        assertEquals(1f, ReaderProgressCalculator.chapterProgress(chapterIndex = 8, totalChapters = 4))
        assertEquals(0f, ReaderProgressCalculator.chapterProgress(chapterIndex = 1, totalChapters = 0))
    }
}
