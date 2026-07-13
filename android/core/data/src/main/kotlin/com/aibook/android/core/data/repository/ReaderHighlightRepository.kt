package com.aibook.android.core.data.repository

import com.aibook.android.core.data.db.ReaderHighlightDao
import com.aibook.android.core.data.db.ReaderHighlightEntity
import com.aibook.android.core.reader.ReaderHighlight
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class ReaderHighlightRepository(private val dao: ReaderHighlightDao) {
    fun observeForBook(bookId: String): Flow<List<ReaderHighlight>> = dao.observeForBook(bookId).map { it.map(ReaderHighlightEntity::toDomain) }
    suspend fun add(highlight: ReaderHighlight) = dao.insert(highlight.toEntity())
    suspend fun remove(id: String) = dao.deleteById(id)
}

private fun ReaderHighlightEntity.toDomain() = ReaderHighlight(id, bookId, chapterHref, chapterIndex, lineIndex, startOffset, endOffset, excerpt, note, color, Instant.ofEpochMilli(createdAt))
private fun ReaderHighlight.toEntity() = ReaderHighlightEntity(id, bookId, chapterHref, chapterIndex, lineIndex, startOffset, endOffset, excerpt, note, color, createdAt.toEpochMilli())
