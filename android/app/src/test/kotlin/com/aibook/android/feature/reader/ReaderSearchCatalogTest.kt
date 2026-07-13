package com.aibook.android.feature.reader

import com.aibook.android.core.reader.ReaderChapter
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSearchCatalogTest {

    private val chapters = listOf(
        ReaderChapter(index = 0, title = "第一章", href = "chapter-1", content = "Earth is blue\n第二行"),
        ReaderChapter(index = 1, title = "第二章", href = "chapter-2", content = "earth is home")
    )

    @Test
    fun `find returns matches across chapters ignoring case`() {
        assertEquals(
            listOf(ReaderSearchMatch(chapterIndex = 0, lineIndex = 0), ReaderSearchMatch(1, 0)),
            ReaderSearchCatalog.find(chapters, "earth")
        )
    }

    @Test
    fun `find returns no matches for a blank query`() {
        assertEquals(emptyList<ReaderSearchMatch>(), ReaderSearchCatalog.find(chapters, "  "))
    }

    @Test
    fun `nextIndex wraps in either direction`() {
        assertEquals(0, ReaderSearchCatalog.nextIndex(currentIndex = 1, count = 2, forward = true))
        assertEquals(1, ReaderSearchCatalog.nextIndex(currentIndex = 0, count = 2, forward = false))
    }

    @Test
    fun `nextIndex starts at the first or last match before any selection`() {
        assertEquals(0, ReaderSearchCatalog.nextIndex(currentIndex = -1, count = 2, forward = true))
        assertEquals(1, ReaderSearchCatalog.nextIndex(currentIndex = -1, count = 2, forward = false))
    }
}
