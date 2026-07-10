package com.aibook.android.core.data.repository

import com.aibook.android.core.data.db.ReaderBookmarkDao
import com.aibook.android.core.data.db.ReaderBookmarkEntity
import com.aibook.android.core.reader.ReaderBookmark
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class ReaderBookmarkRepository(private val dao: ReaderBookmarkDao) {

    fun observeForBook(bookId: String): Flow<List<ReaderBookmark>> =
        dao.observeForBook(bookId).map { bookmarks -> bookmarks.map { it.toDomain() } }

    suspend fun add(bookmark: ReaderBookmark) {
        dao.insert(bookmark.toEntity())
    }

    suspend fun remove(id: String) {
        dao.deleteById(id)
    }

    suspend fun removeForBook(bookId: String) {
        dao.deleteForBook(bookId)
    }
}

private fun ReaderBookmarkEntity.toDomain() = ReaderBookmark(
    id = id,
    bookId = bookId,
    chapterHref = chapterHref,
    chapterTitle = chapterTitle,
    progress = progress,
    chapterIndex = chapterIndex,
    lineIndex = lineIndex,
    scrollOffset = scrollOffset,
    createdAt = Instant.ofEpochMilli(createdAt)
)

private fun ReaderBookmark.toEntity() = ReaderBookmarkEntity(
    id = id,
    bookId = bookId,
    chapterHref = chapterHref,
    chapterTitle = chapterTitle,
    progress = progress,
    chapterIndex = chapterIndex,
    lineIndex = lineIndex,
    scrollOffset = scrollOffset,
    createdAt = createdAt.toEpochMilli()
)
