package com.aibook.android.feature.reader

import com.aibook.android.core.reader.ReaderChapter

data class ReaderContentsGroup(
    val title: String,
    val chapters: List<ReaderChapter>
)

enum class ReaderChapterReadState {
    READ,
    CURRENT,
    UNREAD
}

object ReaderContentsCatalog {
    private val volumeHeading = Regex(
        "^(?:第[0-9零〇一二两三四五六七八九十百千]+卷|卷[0-9零〇一二两三四五六七八九十百千]+|[上中下终序]卷)(?:\\s|　|[:：·._-]|$).*"
    )

    fun group(chapters: List<ReaderChapter>): List<ReaderContentsGroup> {
        if (chapters.isEmpty()) return emptyList()

        val groups = mutableListOf<ReaderContentsGroup>()
        var title = "正文"
        var items = mutableListOf<ReaderChapter>()

        chapters.forEach { chapter ->
            if (volumeHeading.matches(chapter.title.trim())) {
                if (items.isNotEmpty()) {
                    groups += ReaderContentsGroup(title, items.toList())
                }
                title = chapter.title.ifBlank { "正文" }
                items = mutableListOf()
            }
            items += chapter
        }

        if (items.isNotEmpty()) {
            groups += ReaderContentsGroup(title, items.toList())
        }
        return groups
    }

    fun currentGroupIndex(
        groups: List<ReaderContentsGroup>,
        currentChapterIndex: Int
    ): Int = groups.indexOfFirst { group ->
        group.chapters.any { it.index == currentChapterIndex }
    }.takeIf { it >= 0 } ?: 0

    fun readState(
        chapterIndex: Int,
        currentChapterIndex: Int
    ): ReaderChapterReadState = when {
        chapterIndex < currentChapterIndex -> ReaderChapterReadState.READ
        chapterIndex == currentChapterIndex -> ReaderChapterReadState.CURRENT
        else -> ReaderChapterReadState.UNREAD
    }
}
