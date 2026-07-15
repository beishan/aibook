package com.aibook.android.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "download_tasks", indices = [Index("remoteEntryId"), Index("status")])
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    val remoteEntryId: String,
    val connectionId: String,
    val title: String,
    val href: String,
    val fileName: String,
    val status: String = "QUEUED",
    val progress: Int = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
