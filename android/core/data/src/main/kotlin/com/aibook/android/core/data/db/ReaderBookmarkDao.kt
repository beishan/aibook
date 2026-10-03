package com.aibook.android.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReaderBookmarkDao {

    @Query("SELECT * FROM reader_bookmarks ORDER BY createdAt ASC")
    suspend fun getAll(): List<ReaderBookmarkEntity>

    @Query("SELECT * FROM reader_bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observeForBook(bookId: String): Flow<List<ReaderBookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: ReaderBookmarkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bookmarks: List<ReaderBookmarkEntity>)

    @Query("DELETE FROM reader_bookmarks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM reader_bookmarks WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)
}
