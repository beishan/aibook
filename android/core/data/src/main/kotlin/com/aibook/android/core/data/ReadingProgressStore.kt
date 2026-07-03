package com.aibook.android.core.data

import com.aibook.android.core.model.LocalBook
import com.aibook.android.core.model.ReadingProgress
import com.aibook.android.core.model.ReadingStatus
import java.time.Instant

class ReadingProgressStore {
    fun updateProgress(
        book: LocalBook,
        chapterHref: String?,
        chapterTitle: String?,
        percent: Float,
        chapterIndex: Int? = null,
        lineIndex: Int? = null,
        scrollOffset: Int = 0
    ): LocalBook {
        return book.copy(
            status = if (percent >= 1f) ReadingStatus.FINISHED else ReadingStatus.READING,
            lastReadAt = Instant.now(),
            progress = ReadingProgress(
                chapterHref = chapterHref,
                chapterTitle = chapterTitle,
                chapterIndex = chapterIndex,
                lineIndex = lineIndex,
                scrollOffset = scrollOffset.coerceAtLeast(0),
                percent = percent.coerceIn(0f, 1f),
                positionLabel = "${(percent.coerceIn(0f, 1f) * 100).toInt()}%"
            )
        )
    }
}
