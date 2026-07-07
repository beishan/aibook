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
    val folderId: String? = null,
    val status: ReadingStatus = ReadingStatus.UNREAD,
    val favorite: Boolean = false,
    val shelved: Boolean = false,
    val visibleInStore: Boolean = true,
    val importedAt: Instant = Instant.now(),
    val lastReadAt: Instant? = null,
    val progress: ReadingProgress = ReadingProgress()
)

data class ShelfFolder(
    val id: String,
    val name: String,
    val createdAtEpochMillis: Long
)

sealed interface ShelfFolderSelection {
    data object All : ShelfFolderSelection
    data object Unfiled : ShelfFolderSelection
    data class Folder(val folderId: String) : ShelfFolderSelection
}

object ShelfFolderCatalog {
    fun filterBooks(
        books: List<LocalBook>,
        selection: ShelfFolderSelection
    ): List<LocalBook> {
        return when (selection) {
            ShelfFolderSelection.All -> books
            ShelfFolderSelection.Unfiled -> books.filter { it.folderId == null }
            is ShelfFolderSelection.Folder -> books.filter { it.folderId == selection.folderId }
        }
    }

    fun folderCounts(books: List<LocalBook>): Map<String, Int> {
        return books
            .mapNotNull { it.folderId }
            .groupingBy { it }
            .eachCount()
    }
}

data class ReadingProgress(
    val chapterHref: String? = null,
    val chapterTitle: String? = null,
    val chapterIndex: Int? = null,
    val lineIndex: Int? = null,
    val scrollOffset: Int = 0,
    val percent: Float = 0f,
    val positionLabel: String? = null
)

data class ReaderSettings(
    val fontScale: Float = 1.0f,
    val fontType: ReaderFontType = ReaderFontType.SYSTEM,
    val customFontName: String? = null,
    val customFontPath: String? = null,
    val lineHeight: Float = 1.45f,
    val theme: ReaderTheme = ReaderTheme.PAPER,
    val paragraphSpacing: ParagraphSpacing = ParagraphSpacing.SMALL,
    val textAlignment: TextAlignment = TextAlignment.LEFT,
    val pageTurnMode: PageTurnMode = PageTurnMode.SIMULATION,
    val autoBrightness: Boolean = true,
    val screenAlwaysOn: Boolean = false
)

enum class ReaderFontType {
    SYSTEM,
    SERIF,
    SANS_SERIF,
    MONOSPACE,
    CUSTOM
}

data class ReaderFontOption(
    val type: ReaderFontType,
    val label: String,
    val description: String
)

object ReaderFontCatalog {
    val builtInFonts: List<ReaderFontOption> = listOf(
        ReaderFontOption(ReaderFontType.SYSTEM, "系统字体", "使用设备默认字体"),
        ReaderFontOption(ReaderFontType.SERIF, "衬线字体", "适合长篇阅读的传统排版"),
        ReaderFontOption(ReaderFontType.SANS_SERIF, "无衬线字体", "清爽现代，适合屏幕阅读"),
        ReaderFontOption(ReaderFontType.MONOSPACE, "等宽字体", "字符宽度一致，适合代码与笔记")
    )

    fun isSupportedFontFile(fileName: String): Boolean {
        val normalized = fileName.trim().lowercase()
        return normalized.endsWith(".ttf") || normalized.endsWith(".otf")
    }

    fun selectedLabel(settings: ReaderSettings): String {
        return when (settings.fontType) {
            ReaderFontType.CUSTOM -> settings.customFontName?.takeIf { it.isNotBlank() } ?: "本地导入字体"
            else -> builtInFonts.firstOrNull { it.type == settings.fontType }?.label ?: "系统字体"
        }
    }
}

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

fun PageTurnMode.usesPagedReading(): Boolean = this != PageTurnMode.VERTICAL
