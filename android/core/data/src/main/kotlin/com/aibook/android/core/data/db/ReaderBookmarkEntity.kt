package com.aibook.android.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reader_bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId")]
)
data class ReaderBookmarkEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val chapterHref: String?,
    val chapterTitle: String?,
    val progress: Float,
    val chapterIndex: Int?,
    val lineIndex: Int,
    val scrollOffset: Int,
    val createdAt: Long
)
