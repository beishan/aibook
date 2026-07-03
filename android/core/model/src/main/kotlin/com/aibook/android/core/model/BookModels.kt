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
    val shelved: Boolean = false,
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
    val theme: ReaderTheme = ReaderTheme.PAPER,
    val paragraphSpacing: ParagraphSpacing = ParagraphSpacing.SMALL,
    val textAlignment: TextAlignment = TextAlignment.LEFT,
    val pageTurnMode: PageTurnMode = PageTurnMode.SIMULATION,
    val autoBrightness: Boolean = true,
    val screenAlwaysOn: Boolean = false
)

enum class ReaderTheme {
    LIGHT,
    PAPER,
    GREEN,
    GRAY,
    DARK
}

enum class ParagraphSpacing {
    NONE,
    SMALL,
    LARGE
}

enum class TextAlignment {
    LEFT,
    CENTER,
    RIGHT,
    JUSTIFY
}

enum class PageTurnMode {
    SIMULATION,
    SLIDE,
    COVER,
    PAN,
    VERTICAL
}
