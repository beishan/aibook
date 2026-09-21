package com.aibook.android.core.network.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class BookmarkDTO(
    val id: Long,
    val title: String? = null,
    val excerpt: String? = null,
    val chapter: String? = null,
    val chapterIndex: Int? = null,
    val cfi: String? = null,
    val scrollPosition: Long? = null,
    val page: Int? = null,
    val createdAt: String? = null
)

@Serializable
data class CreateBookmarkRequest(
    val title: String? = null,
    val excerpt: String? = null,
    val chapter: String? = null,
    val chapterIndex: Int? = null,
    val cfi: String? = null,
    val scrollPosition: Long? = null,
    val page: Int? = null
)

@Serializable
data class HighlightDTO(
    val id: Long,
    val cfiRange: String,
    val text: String,
    val color: String = "#ffff00",
    val chapter: String? = null,
    val note: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class CreateHighlightRequest(
    val cfiRange: String,
    val text: String,
    val color: String,
    val chapter: String? = null,
    val note: String? = null
)
