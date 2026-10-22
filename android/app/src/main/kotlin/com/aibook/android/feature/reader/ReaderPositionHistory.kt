package com.aibook.android.feature.reader

import com.aibook.android.core.reader.ReaderBookmark

object ReaderPositionHistory {
    fun push(history: List<ReaderBookmark>, position: ReaderBookmark): List<ReaderBookmark> {
        val previous = history.lastOrNull()
        if (previous != null && previous.chapterIndex == position.chapterIndex &&
            previous.lineIndex == position.lineIndex && previous.scrollOffset == position.scrollOffset
        ) return history
        return (history + position).takeLast(10)
    }
}