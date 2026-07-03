package com.aibook.android.core.reader

object ReaderProgressCalculator {
    fun chapterProgress(chapterIndex: Int, totalChapters: Int): Float {
        if (totalChapters <= 0) return 0f
        return (chapterIndex.toFloat() / totalChapters.toFloat()).coerceIn(0f, 1f)
    }
}
