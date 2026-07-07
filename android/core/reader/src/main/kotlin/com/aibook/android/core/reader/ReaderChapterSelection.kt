package com.aibook.android.core.reader

object ReaderChapterSelection {
    fun selectInitialIndex(
        chapters: List<ReaderChapter>,
        preferredHref: String?,
        preferredIndex: Int? = null
    ): Int {
        val preferredByIndex = preferredIndex
            ?.takeIf { it in chapters.indices }
            ?.takeIf { chapters[it].isReadable() }
        if (preferredByIndex != null) return preferredByIndex

        val preferredIndex = preferredHref
            ?.let { href -> chapters.indexOfFirst { it.href == href && it.isReadable() } }
            ?.takeIf { it >= 0 }
        if (preferredIndex != null) return preferredIndex

        return chapters.indexOfFirst { it.isReadable() }
            .takeIf { it >= 0 }
            ?: 0
    }

    private fun ReaderChapter.isReadable(): Boolean {
        return content.isNotBlank() || !imageUri.isNullOrBlank()
    }
}
