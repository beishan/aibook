package com.aibook.android.feature.reader

import com.aibook.android.core.reader.ReaderChapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

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

    @Test
    fun ignoresPreviousChapterResponseFromBeforeDirectoryJump() {
        val selectedWindow = listOf(chapter(20))
        assertSame(selectedWindow, ReaderChapterWindow.prepend(selectedWindow, chapter(2)))
    }

    @Test
    fun ignoresNextChapterResponseFromBeforeDirectoryJumpAndDuplicateResponses() {
        val selectedWindow = listOf(chapter(20))
        assertSame(selectedWindow, ReaderChapterWindow.append(selectedWindow, chapter(4)))
        val expandedWindow = ReaderChapterWindow.append(selectedWindow, chapter(21))
        assertEquals(listOf(20, 21), expandedWindow.map { it.index })
        assertSame(expandedWindow, ReaderChapterWindow.append(expandedWindow, chapter(21)))
    }

    private fun chapter(index: Int) = ReaderChapter(
        index = index,
        title = "第${index + 1}章",
        href = "chapter-$index",
        content = "正文"
    )
}
