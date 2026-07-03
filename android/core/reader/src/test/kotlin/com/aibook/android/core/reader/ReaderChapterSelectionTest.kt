package com.aibook.android.core.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderChapterSelectionTest {

    @Test
    fun selectsFirstReadableChapterWhenNoSavedHref() {
        val chapters = listOf(
            ReaderChapter(index = 0, title = "封面", href = "cover.xhtml", content = ""),
            ReaderChapter(index = 1, title = "正文", href = "chapter-1.xhtml", content = "第一章内容")
        )

        val selected = ReaderChapterSelection.selectInitialIndex(chapters, preferredHref = null)

        assertEquals(1, selected)
    }

    @Test
    fun fallsBackToReadableChapterWhenSavedHrefIsBlank() {
        val chapters = listOf(
            ReaderChapter(index = 0, title = "封面", href = "cover.xhtml", content = ""),
            ReaderChapter(index = 1, title = "正文", href = "chapter-1.xhtml", content = "第一章内容")
        )

        val selected = ReaderChapterSelection.selectInitialIndex(chapters, preferredHref = "cover.xhtml")

        assertEquals(1, selected)
    }
}
