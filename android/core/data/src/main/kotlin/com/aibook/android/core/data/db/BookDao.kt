package com.aibook.android.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("UPDATE books SET readingDurationSeconds = readingDurationSeconds + :seconds WHERE id = :id")
    suspend fun addReadingDuration(id: String, seconds: Long)

    @Query("SELECT * FROM books ORDER BY lastReadAt DESC, title ASC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE shelved = 1 ORDER BY lastReadAt DESC, title ASC")
    fun observeShelved(): Flow<List<BookEntity>>

    @Query("""
        SELECT * FROM books
        WHERE shelved = 1
          AND (:query = '' OR title LIKE '%' || :query || '%' COLLATE NOCASE OR author LIKE '%' || :query || '%' COLLATE NOCASE)
          AND (:folderMode = 0 OR (:folderMode = 1 AND folderId IS NULL) OR (:folderMode = 2 AND folderId = :folderId))
        ORDER BY lastReadAt DESC, title ASC
        LIMIT :limit OFFSET :offset
    """)
    fun observeShelvedPage(query: String, folderMode: Int, folderId: String?, limit: Int, offset: Int): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeById(id: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE sha256 = :sha256 LIMIT 1")
    suspend fun getBySha256(sha256: String): BookEntity?

    @Query("SELECT * FROM books WHERE source = :source ORDER BY lastReadAt DESC, title ASC")
    fun observeBySource(source: String): Flow<List<BookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: BookEntity)

    @Update
    suspend fun update(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE books SET status = :status, lastReadAt = :lastReadAt, progressPercent = :percent, progressChapterHref = :chapterHref, progressChapterTitle = :chapterTitle, progressChapterIndex = :chapterIndex, progressLineIndex = :lineIndex, progressScrollOffset = :scrollOffset, progressPdfZoom = :pdfZoom, progressPositionLabel = :positionLabel WHERE id = :id")
    suspend fun updateProgress(
        id: String,
        status: String,
        lastReadAt: Long,
        percent: Float,
        chapterHref: String?,
        chapterTitle: String?,
        chapterIndex: Int?,
        lineIndex: Int?,
        scrollOffset: Int,
        pdfZoom: Float?,
        positionLabel: String?
    )

    @Query("UPDATE books SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE books SET shelved = :shelved WHERE id = :id")
    suspend fun setShelved(id: String, shelved: Boolean)

    @Query("UPDATE books SET visibleInStore = :visibleInStore WHERE id = :id")
    suspend fun setStoreVisible(id: String, visibleInStore: Boolean)

    @Query("UPDATE books SET visibleInStore = 1 WHERE id = :id")
    suspend fun restoreStoreVisibility(id: String)

    @Query("UPDATE books SET uri = :uri, sha256 = :sha256 WHERE id = :id")
    suspend fun updateFileLocation(id: String, uri: String, sha256: String)

    @Query("UPDATE books SET title = :title, author = :author, description = :description, rating = :rating, tags = :tags WHERE id = :id")
    suspend fun updateMetadata(id: String, title: String, author: String?, description: String?, rating: Float?, tags: String)

    @Query("UPDATE books SET coverUri = :coverUri WHERE id = :id")
    suspend fun updateCover(id: String, coverUri: String?)

    @Query("UPDATE books SET visibleInStore = 0, shelved = 0, folderId = NULL WHERE id = :id")
    suspend fun removeFromStore(id: String)

    @Query("UPDATE books SET folderId = :folderId WHERE id IN (:ids)")
    suspend fun setFolder(ids: List<String>, folderId: String?)

    @Query("UPDATE books SET folderId = NULL WHERE folderId = :folderId")
    suspend fun clearFolder(folderId: String)

    @Query("SELECT COUNT(*) FROM books")
    suspend fun count(): Int
}
