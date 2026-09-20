package com.aibook.android.core.network.api.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class StructuredChapterInfoDTO(
    val key: String,
    val title: String,
    val startIndex: Int,
    val endIndex: Int
)

/** Converts the backend's processed structured publication into independently navigable chapters. */
fun ProcessedContentResponse.structuredChapters(): List<StructuredChapterInfoDTO> {
    if (text.isBlank() || chapterInfo.isBlank()) return emptyList()
    return runCatching {
        Json { ignoreUnknownKeys = true }
            .decodeFromString<List<StructuredChapterInfoDTO>>(chapterInfo)
            .filter { chapter ->
                chapter.key.isNotBlank() &&
                    chapter.startIndex in 0..text.length &&
                    chapter.endIndex in chapter.startIndex..text.length
            }
    }.getOrDefault(emptyList())
}
