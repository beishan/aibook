package com.aibook.android.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val author: String? = null,
    val format: String,
    val uri: String,
    val sha256: String? = null,
    val coverUri: String? = null,
    val status: String = "UNREAD",
    val favorite: Boolean = false,
    val importedAt: Long = System.currentTimeMillis(),
    val lastReadAt: Long? = null,
    val progressPercent: Float = 0f,
    val progressChapterHref: String? = null,
    val progressChapterTitle: String? = null,
    val progressChapterIndex: Int? = null,
    val progressLineIndex: Int? = null,
    val progressScrollOffset: Int = 0,
    val progressPositionLabel: String? = null,
    val source: String = "LOCAL",
    val remoteBookId: Long? = null,
    val shelved: Boolean = false
)
