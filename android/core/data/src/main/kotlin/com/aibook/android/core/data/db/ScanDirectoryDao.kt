package com.aibook.android.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDirectoryDao {

    @Query("SELECT * FROM scan_directories ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ScanDirectoryEntity>>

    @Query("SELECT * FROM scan_directories ORDER BY createdAt DESC")
    suspend fun getAll(): List<ScanDirectoryEntity>

    @Query("SELECT * FROM scan_directories WHERE id = :id")
    suspend fun getById(id: String): ScanDirectoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(directory: ScanDirectoryEntity)

    @Query("UPDATE scan_directories SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("DELETE FROM scan_directories WHERE id = :id")
    suspend fun deleteById(id: String)
}
