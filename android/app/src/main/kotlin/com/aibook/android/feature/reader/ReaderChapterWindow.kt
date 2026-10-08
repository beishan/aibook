package com.aibook.android.feature.reader

import com.aibook.android.core.reader.ReaderChapter

object ReaderChapterWindow {
    fun shouldNavigateToPage(
        initialized: Boolean,
        appliedRequestId: Long?,
        requestId: Long?
    ): Boolean = !initialized || (requestId != null && requestId != appliedRequestId)

    fun positionForItemKey(itemKey: Any?): Pair<Int, Int>? {
        val parts = (itemKey as? String)?.split('_') ?: return null
        val chapterIndex = parts.getOrNull(1)?.toIntOrNull() ?: return null
        val lineIndex = when (parts.first()) {
            "title", "image" -> 0
            "p" -> parts.getOrNull(2)?.toIntOrNull() ?: return null
            else -> return null
        }
        return chapterIndex to lineIndex
    }

    fun shouldPrependPrevious(firstVisibleItemIndex: Int, scrollOffset: Int): Boolean =
        firstVisibleItemIndex == 0 && scrollOffset == 0

    fun append(
        loadedChapters: List<ReaderChapter>,
        nextChapter: ReaderChapter
    ): List<ReaderChapter> = if (nextChapter.index == loadedChapters.lastOrNull()?.index?.plus(1)) {
        loadedChapters + nextChapter
    } else {
        loadedChapters
    }

    fun prepend(
        loadedChapters: List<ReaderChapter>,
        previousChapter: ReaderChapter
    ): List<ReaderChapter> {
        if (previousChapter.index != loadedChapters.firstOrNull()?.index?.minus(1)) {
            return loadedChapters
        }
        return listOf(previousChapter) + loadedChapters
    }
}
