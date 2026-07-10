package com.aibook.android.core.reader

import java.time.Instant
import java.util.UUID

data class ReaderBookmark(
    val id: String = UUID.randomUUID().toString(),
    val bookId: String,
    val chapterHref: String?,
    val chapterTitle: String?,
    val progress: Float,
    val chapterIndex: Int? = null,
    val lineIndex: Int = 0,
    val scrollOffset: Int = 0,
    val createdAt: Instant = Instant.now()
) {
    val progressLabel: String = "${(progress.coerceIn(0f, 1f) * 100).toInt()}%"
}

data class ReaderHighlight(
    val id: String = UUID.randomUUID().toString(),
    val bookId: String,
    val chapterHref: String?,
    val startOffset: Int,
    val endOffset: Int,
    val excerpt: String,
    val note: String? = null,
    val createdAt: Instant = Instant.now()
) {
    companion object {
        fun create(
            bookId: String,
            chapterHref: String?,
            text: String,
            startOffset: Int,
            endOffset: Int,
            note: String? = null
        ): ReaderHighlight {
            val safeStart = startOffset.coerceIn(0, text.length)
            val safeEnd = endOffset.coerceIn(safeStart, text.length)

            return ReaderHighlight(
                bookId = bookId,
                chapterHref = chapterHref,
                startOffset = safeStart,
                endOffset = safeEnd,
                excerpt = text.substring(safeStart, safeEnd),
                note = note
            )
        }
    }
}
