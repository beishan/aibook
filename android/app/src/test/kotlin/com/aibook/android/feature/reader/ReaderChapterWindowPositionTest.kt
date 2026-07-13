package com.aibook.android.feature.reader

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderChapterWindowPositionTest {

    @Test
    fun requestsPreviousChapterOnlyAtTheTopOfTheCurrentWindow() {
        assertTrue(ReaderChapterWindow.shouldPrependPrevious(firstVisibleItemIndex = 0, scrollOffset = 0))
        assertFalse(ReaderChapterWindow.shouldPrependPrevious(firstVisibleItemIndex = 0, scrollOffset = 1))
        assertFalse(ReaderChapterWindow.shouldPrependPrevious(firstVisibleItemIndex = 1, scrollOffset = 0))
    }
}
