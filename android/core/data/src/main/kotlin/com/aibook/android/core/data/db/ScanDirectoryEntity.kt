package com.aibook.android.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_directories")
data class ScanDirectoryEntity(
    @PrimaryKey
    val id: String,
    val uri: String,
    val name: String,
    val enabled: Boolean = true,
    val lastScanAt: Long? = null,
    val discoveredCount: Int = 0,
    val addedCount: Int = 0,
    val duplicateCount: Int = 0,
    val unsupportedCount: Int = 0,
    val failedCount: Int = 0,
    val lastErrorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
