package com.aibook.android.core.network.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ReadingProgressDTO(
    val id: Long? = null,
    val currentChapter: String? = null,
    val chapterProgress: Int = 0,
    val totalProgress: Int = 0,
    val readingTimeSeconds: Long = 0,
    val lastReadAt: String? = null
)

@Serializable
data class SaveProgressRequest(
    val currentChapter: String? = null,
    val chapterProgress: Int = 0,
    val totalProgress: Int = 0
)

@Serializable
data class UpdateReadingTimeRequest(
    val seconds: Long
)
