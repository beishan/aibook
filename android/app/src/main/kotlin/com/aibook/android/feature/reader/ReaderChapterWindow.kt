package com.aibook.android.feature.reader

import com.aibook.android.core.reader.ReaderChapter

object ReaderChapterWindow {
    fun shouldPrependPrevious(firstVisibleItemIndex: Int, scrollOffset: Int): Boolean =
        firstVisibleItemIndex == 0 && scrollOffset == 0

    fun prepend(
        loadedChapters: List<ReaderChapter>,
        previousChapter: ReaderChapter
    ): List<ReaderChapter> {
        if (loadedChapters.any { it.index == previousChapter.index }) {
            return loadedChapters
        }
        return listOf(previousChapter) + loadedChapters
    }
}
