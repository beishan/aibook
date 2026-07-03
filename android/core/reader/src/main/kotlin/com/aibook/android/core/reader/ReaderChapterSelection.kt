package com.aibook.android.core.reader

object ReaderChapterSelection {
    fun selectInitialIndex(chapters: List<ReaderChapter>, preferredHref: String?): Int {
        val preferredIndex = preferredHref
            ?.let { href -> chapters.indexOfFirst { it.href == href && it.content.isNotBlank() } }
            ?.takeIf { it >= 0 }
        if (preferredIndex != null) return preferredIndex

        return chapters.indexOfFirst { it.content.isNotBlank() }
            .takeIf { it >= 0 }
            ?: 0
    }
}
