package com.aibook.android.core.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class AnnotationModelsTest {
    @Test
    fun `highlight creates compact excerpt from selected range`() {
        val highlight = ReaderHighlight.create(
            bookId = "book-1",
            chapterHref = "chapter-1",
            text = "三体文明监听员向宇宙发送了一条信息。",
            startOffset = 4,
            endOffset = 9,
            note = "关键设定"
        )

        assertEquals("监听员向宇", highlight.excerpt)
        assertEquals("关键设定", highlight.note)
    }

    @Test
    fun `bookmark stores chapter and progress label`() {
        val bookmark = ReaderBookmark(
            bookId = "book-1",
            chapterHref = "chapter-1",
            chapterTitle = "第一章",
            progress = 0.25f
        )

        assertEquals("25%", bookmark.progressLabel)
    }
}
