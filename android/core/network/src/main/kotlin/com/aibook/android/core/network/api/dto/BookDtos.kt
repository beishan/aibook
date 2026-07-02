package com.aibook.android.core.network.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BookDTO(
    val id: Long? = null,
    val title: String = "",
    val author: String? = null,
    val isbn: String? = null,
    val publisher: String? = null,
    val publishDate: String? = null,
    val description: String? = null,
    val coverUrl: String? = null,
    val format: String? = null,
    val filePath: String? = null,
    val fileSize: Long? = null,
    val language: String? = null,
    val rating: Int? = null,
    val readingStatus: String? = null,
    val categoryName: String? = null,
    val tagNames: List<String>? = null,
    val isFavorite: Boolean? = null,
    val isWanted: Boolean? = null,
    val notes: String? = null,
    val chapterInfo: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class BookPage(
    val content: List<BookDTO> = emptyList(),
    val pageable: Pageable? = null,
    val totalPages: Int = 0,
    val totalElements: Long = 0,
    val last: Boolean = true,
    val first: Boolean = true,
    val numberOfElements: Int = 0,
    val empty: Boolean = true
)

@Serializable
data class Pageable(
    val pageNumber: Int = 0,
    val pageSize: Int = 10,
    val offset: Long = 0,
    val paged: Boolean = true,
    val unpaged: Boolean = false
)

@Serializable
data class ProcessedContentResponse(
    val text: String = "",
    @SerialName("chapterInfo")
    val chapterInfo: String = "[]"
)
