package com.aibook.android.core.network.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class BookVersionDTO(
    val id: Long,
    val displayName: String,
    val format: String,
    val primaryVersion: Boolean = false,
    val selected: Boolean = false,
    val fileSize: Long? = null,
    val chapterCount: Int? = null
)
