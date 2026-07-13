package com.aibook.android.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReaderHighlightDao {
    @Query("SELECT * FROM reader_highlights WHERE bookId = :bookId ORDER BY createdAt DESC") fun observeForBook(bookId: String): Flow<List<ReaderHighlightEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(highlight: ReaderHighlightEntity)
    @Query("DELETE FROM reader_highlights WHERE id = :id") suspend fun deleteById(id: String)
}
