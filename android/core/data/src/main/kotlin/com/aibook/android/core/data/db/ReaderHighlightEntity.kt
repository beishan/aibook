package com.aibook.android.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "reader_highlights", foreignKeys = [ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)], indices = [Index("bookId")])
data class ReaderHighlightEntity(@PrimaryKey val id: String, val bookId: String, val chapterHref: String?, val chapterIndex: Int?, val lineIndex: Int, val startOffset: Int, val endOffset: Int, val excerpt: String, val note: String?, val color: Long, val createdAt: Long)
