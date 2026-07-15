package com.aibook.android.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadTaskDao {
    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE id = :id")
    suspend fun getById(id: String): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE remoteEntryId = :remoteEntryId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestByRemoteEntry(remoteEntryId: String): DownloadTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: DownloadTaskEntity)

    @Query("UPDATE download_tasks SET status = :status, progress = :progress, downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, errorMessage = :errorMessage, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateState(id: String, status: String, progress: Int, downloadedBytes: Long, totalBytes: Long?, errorMessage: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteById(id: String)
}
