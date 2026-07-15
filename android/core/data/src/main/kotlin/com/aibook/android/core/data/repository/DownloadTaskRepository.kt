package com.aibook.android.core.data.repository

import com.aibook.android.core.data.db.DownloadTaskDao
import com.aibook.android.core.data.db.DownloadTaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class DownloadStatus { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED }

data class DownloadTask(
    val id: String,
    val remoteEntryId: String,
    val connectionId: String,
    val title: String,
    val href: String,
    val fileName: String,
    val status: DownloadStatus,
    val progress: Int,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val errorMessage: String?
)

class DownloadTaskRepository(private val dao: DownloadTaskDao) {
    fun observeAll(): Flow<List<DownloadTask>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }
    suspend fun getById(id: String): DownloadTask? = dao.getById(id)?.toDomain()
    suspend fun getLatestByRemoteEntry(id: String): DownloadTask? = dao.getLatestByRemoteEntry(id)?.toDomain()
    suspend fun save(task: DownloadTaskEntity) = dao.insert(task)
    suspend fun update(id: String, status: DownloadStatus, progress: Int, downloaded: Long = 0, total: Long? = null, error: String? = null) =
        dao.updateState(id, status.name, progress.coerceIn(0, 100), downloaded, total, error)
    suspend fun delete(id: String) = dao.deleteById(id)

    private fun DownloadTaskEntity.toDomain() = DownloadTask(
        id, remoteEntryId, connectionId, title, href, fileName,
        runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.FAILED),
        progress, downloadedBytes, totalBytes, errorMessage
    )
}
