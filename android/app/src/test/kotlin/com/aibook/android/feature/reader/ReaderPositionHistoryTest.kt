package com.aibook.android.feature.reader

import com.aibook.android.core.reader.ReaderBookmark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ReaderPositionHistoryTest {
    private fun position(line: Int) = ReaderBookmark(
        bookId = "book", chapterHref = null, chapterTitle = null,
        progress = 0.5f, chapterIndex = 2, lineIndex = line, scrollOffset = 17
    )

    @Test
    fun retainsNewestTenPositionsInReturnOrder() {
        val history = (0..12).fold(emptyList<ReaderBookmark>()) { result, line ->
            ReaderPositionHistory.push(result, position(line))
        }
        assertEquals((3..12).toList(), history.map { it.lineIndex })
        assertEquals(12, history.last().lineIndex)
        assertEquals(17, history.last().scrollOffset)
    }

    @Test
    fun ignoresConsecutiveDuplicateButRetainsRevisitedPositions() {
        val history = listOf(position(1))
        assertSame(history, ReaderPositionHistory.push(history, position(1)))
        val revisited = ReaderPositionHistory.push(
            ReaderPositionHistory.push(history, position(2)), position(1)
        )
        assertEquals(listOf(1, 2, 1), revisited.map { it.lineIndex })
    }
}