package com.aibook.android.core.network.api.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val structuredContentJson = Json { ignoreUnknownKeys = true }

@Serializable
data class StructuredManifestDTO(
    val readingOrder: List<StructuredManifestLinkDTO> = emptyList()
)

@Serializable
data class StructuredManifestLinkDTO(
    val href: String,
    val title: String = "",
    val properties: StructuredManifestPropertiesDTO = StructuredManifestPropertiesDTO()
) {
    fun chapterId(): Long? = href
        .substringBefore('?')
        .trimEnd('/')
        .substringAfterLast('/')
        .toLongOrNull()

    fun versionId(): Long? = href.substringAfter('?', "")
        .split('&')
        .firstOrNull { it.substringBefore('=') == "versionId" }
        ?.substringAfter('=', "")
        ?.toLongOrNull()
}

@Serializable
data class StructuredManifestPropertiesDTO(
    val chapterKey: String = "",
    val position: Int = 0
)

@Serializable
data class StructuredChapterDTO(
    val id: Long,
    val key: String,
    val index: Int,
    val title: String,
    val content: String = "",
    val contentHash: String = ""
)

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
        structuredContentJson.decodeFromString<List<StructuredChapterInfoDTO>>(chapterInfo)
            .filter { chapter ->
                chapter.key.isNotBlank() &&
                    chapter.startIndex in 0..text.length &&
                    chapter.endIndex in chapter.startIndex..text.length
            }
    }.getOrDefault(emptyList())
}
