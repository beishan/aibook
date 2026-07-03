package com.aibook.android.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY lastReadAt DESC, title ASC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE shelved = 1 ORDER BY lastReadAt DESC, title ASC")
    fun observeShelved(): Flow<List<BookEntity>>

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

    @Query("UPDATE books SET status = :status, lastReadAt = :lastReadAt, progressPercent = :percent, progressChapterHref = :chapterHref, progressChapterTitle = :chapterTitle, progressPositionLabel = :positionLabel WHERE id = :id")
    suspend fun updateProgress(
        id: String,
        status: String,
        lastReadAt: Long,
        percent: Float,
        chapterHref: String?,
        chapterTitle: String?,
        positionLabel: String?
    )

    @Query("UPDATE books SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE books SET shelved = :shelved WHERE id = :id")
    suspend fun setShelved(id: String, shelved: Boolean)

    @Query("SELECT COUNT(*) FROM books")
    suspend fun count(): Int
}
