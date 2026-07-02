package com.aibook.android.core.model

import java.time.Instant

enum class BookFormat(val extension: String, val displayName: String) {
    EPUB("epub", "EPUB"),
    TXT("txt", "TXT"),
    PDF("pdf", "PDF"),
    MARKDOWN("md", "Markdown"),
    HTML("html", "HTML"),
    HTM("htm", "HTML");

    companion object {
        fun fromFileName(fileName: String): BookFormat? {
            val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
                .lowercase()
                .trim()

            if (extension.isBlank()) {
                return null
            }

            return entries.firstOrNull { it.extension == extension }
        }
    }
}

enum class ReadingStatus {
    UNREAD,
    READING,
    FINISHED,
    WANTED
}

data class LocalBook(
    val id: String,
    val title: String,
    val author: String? = null,
    val format: BookFormat,
    val uri: String,
    val sha256: String? = null,
    val coverUri: String? = null,
    val status: ReadingStatus = ReadingStatus.UNREAD,
    val favorite: Boolean = false,
    val importedAt: Instant = Instant.now(),
    val lastReadAt: Instant? = null,
    val progress: ReadingProgress = ReadingProgress()
)

data class ReadingProgress(
    val chapterHref: String? = null,
    val chapterTitle: String? = null,
    val percent: Float = 0f,
    val positionLabel: String? = null
)

data class ReaderSettings(
    val fontScale: Float = 1.0f,
    val lineHeight: Float = 1.45f,
    val theme: ReaderTheme = ReaderTheme.PAPER
)

enum class ReaderTheme {
    LIGHT,
    PAPER,
    DARK
}
