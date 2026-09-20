package com.aibook.android.core.network.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ReadingProgressDTO(
    val id: Long? = null,
    val bookId: Long? = null,
    val versionId: Long? = null,
    val currentChapter: String? = null,
    val currentChapterTitle: String? = null,
    val locator: String? = null,
    val chapterProgress: Int = 0,
    val totalProgress: Int = 0,
    val readingTimeSeconds: Long = 0,
    val lastReadAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class SaveProgressRequest(
    val currentChapter: String? = null,
    val currentChapterTitle: String? = null,
    val chapterProgress: Int = 0,
    val totalProgress: Int = 0,
    val locator: String? = null
)

@Serializable
data class UpdateReadingTimeRequest(
    val seconds: Long
)
