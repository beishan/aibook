package com.aibook.android.feature.reader

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderChapterWindowPositionTest {

    @Test
    fun requestsPreviousChapterOnlyAtTheTopOfTheCurrentWindow() {
        assertTrue(ReaderChapterWindow.shouldPrependPrevious(firstVisibleItemIndex = 0, scrollOffset = 0))
        assertFalse(ReaderChapterWindow.shouldPrependPrevious(firstVisibleItemIndex = 0, scrollOffset = 1))
        assertFalse(ReaderChapterWindow.shouldPrependPrevious(firstVisibleItemIndex = 1, scrollOffset = 0))
    }

    @Test
    fun preloadingDoesNotReplayAnAppliedNavigationRequest() {
        assertFalse(ReaderChapterWindow.shouldNavigateToPage(true, 42L, 42L))
        assertFalse(ReaderChapterWindow.shouldNavigateToPage(true, null, null))
        assertTrue(ReaderChapterWindow.shouldNavigateToPage(false, null, null))
        assertTrue(ReaderChapterWindow.shouldNavigateToPage(true, 42L, 43L))
    }

    @Test
    fun derivesPositionFromVisibleKeyEvenWhenChapterWindowIndicesShift() {
        // 前插多章后列表位置改变，页面仍显示第 20 章的第 8 段。
        assertEquals(20 to 8, ReaderChapterWindow.positionForItemKey("p_20_8"))
        assertEquals(20 to 0, ReaderChapterWindow.positionForItemKey("title_20"))
        assertEquals(20 to 0, ReaderChapterWindow.positionForItemKey("image_20"))
        assertNull(ReaderChapterWindow.positionForItemKey(null))
        assertNull(ReaderChapterWindow.positionForItemKey("p_unknown_8"))
        assertNull(ReaderChapterWindow.positionForItemKey("page_20_8"))
    }

}
