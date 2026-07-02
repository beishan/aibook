package com.aibook.android.core.data

import com.aibook.android.core.model.BookFormat
import com.aibook.android.core.model.LocalBook
import com.aibook.android.core.model.ReadingStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadingProgressStoreTest {
    @Test
    fun `updates progress and marks book as reading`() {
        val store = ReadingProgressStore()
        val book = LocalBook(
            id = "book-1",
            title = "三体",
            format = BookFormat.EPUB,
            uri = "content://books/1"
        )

        val updated = store.updateProgress(
            book = book,
            chapterHref = "chapter-01.xhtml",
            chapterTitle = "科学边界",
            percent = 0.42f
        )

        assertEquals(ReadingStatus.READING, updated.status)
        assertEquals("chapter-01.xhtml", updated.progress.chapterHref)
        assertEquals("科学边界", updated.progress.chapterTitle)
        assertEquals(0.42f, updated.progress.percent)
    }

    @Test
    fun `clamps progress percentage`() {
        val store = ReadingProgressStore()
        val book = LocalBook(
            id = "book-1",
            title = "三体",
            format = BookFormat.EPUB,
            uri = "content://books/1"
        )

        assertEquals(0f, store.updateProgress(book, null, null, -1f).progress.percent)
        assertEquals(1f, store.updateProgress(book, null, null, 2f).progress.percent)
    }
}
