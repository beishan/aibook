package com.aibook.android.feature.reader

import com.aibook.android.core.reader.ReaderChapter

data class ReaderSearchMatch(
    val chapterIndex: Int,
    val lineIndex: Int
)

object ReaderSearchCatalog {

    fun find(chapters: List<ReaderChapter>, query: String): List<ReaderSearchMatch> {
        if (query.isBlank()) return emptyList()

        return chapters.flatMap { chapter ->
            chapter.content.lineSequence()
                .filter { it.isNotBlank() }
                .mapIndexedNotNull { lineIndex, line ->
                    ReaderSearchMatch(chapter.index, lineIndex)
                        .takeIf { line.contains(query, ignoreCase = true) }
                }
                .toList()
        }
    }

    fun nextIndex(currentIndex: Int, count: Int, forward: Boolean): Int {
        if (count <= 0) return -1
        if (currentIndex !in 0 until count) return if (forward) 0 else count - 1
        return if (forward) (currentIndex + 1).mod(count) else (currentIndex - 1).mod(count)
    }
}
