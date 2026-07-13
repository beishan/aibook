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

sealed interface ReaderContentsListItem {
    data class GroupHeader(
        val groupIndex: Int,
        val group: ReaderContentsGroup,
        val expanded: Boolean
    ) : ReaderContentsListItem

    data class Chapter(
        val groupIndex: Int,
        val chapter: ReaderChapter,
        val isLast: Boolean
    ) : ReaderContentsListItem
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

    fun chapterListPosition(
        chapters: List<ReaderChapter>,
        currentChapterIndex: Int
    ): Int = chapters.indexOfFirst { it.index == currentChapterIndex }
        .takeIf { it >= 0 }
        ?: 0

    fun visibleItemPosition(
        items: List<ReaderContentsListItem>,
        currentChapterIndex: Int
    ): Int = items.indexOfFirst { item ->
        item is ReaderContentsListItem.Chapter && item.chapter.index == currentChapterIndex
    }.takeIf { it >= 0 } ?: 0

    fun visibleItems(
        groups: List<ReaderContentsGroup>,
        expandedGroupIndexes: Set<Int>
    ): List<ReaderContentsListItem> = buildList {
        groups.forEachIndexed { groupIndex, group ->
            val expanded = groupIndex in expandedGroupIndexes
            add(ReaderContentsListItem.GroupHeader(groupIndex, group, expanded))
            if (expanded) {
                group.chapters.forEachIndexed { chapterIndex, chapter ->
                    add(
                        ReaderContentsListItem.Chapter(
                            groupIndex = groupIndex,
                            chapter = chapter,
                            isLast = chapterIndex == group.chapters.lastIndex
                        )
                    )
                }
            }
        }
    }
}
