package com.aibook.android.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shelf_folders")
data class ShelfFolderEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val createdAt: Long
)
