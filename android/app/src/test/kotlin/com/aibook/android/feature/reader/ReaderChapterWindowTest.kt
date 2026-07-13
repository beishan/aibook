package com.aibook.android.feature.reader

import com.aibook.android.core.reader.ReaderChapter
import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderChapterWindowTest {

    @Test
    fun prependsPreviousChapterBeforeDirectoryTarget() {
        val target = chapter(3)
        val chapters = ReaderChapterWindow.prepend(
            loadedChapters = listOf(target),
            previousChapter = chapter(2)
        )

        assertEquals(listOf(2, 3), chapters.map { it.index })
    }

    @Test
    fun doesNotDuplicatePreviouslyLoadedChapter() {
        val chapters = ReaderChapterWindow.prepend(
            loadedChapters = listOf(chapter(2), chapter(3)),
            previousChapter = chapter(2)
        )

        assertEquals(listOf(2, 3), chapters.map { it.index })
    }

    private fun chapter(index: Int) = ReaderChapter(
        index = index,
        title = "第${index + 1}章",
        href = "chapter-$index",
        content = "正文"
    )
}
