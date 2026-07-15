package com.aibook.android.feature.opds

import com.aibook.android.core.network.opds.OpdsEntry
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal enum class OpdsCatalogSort {
    MODIFIED,
    TITLE
}

internal fun presentOpdsEntries(
    entries: List<OpdsEntry>,
    format: String?,
    sort: OpdsCatalogSort
): List<OpdsEntry> {
    val filtered = entries.filter { format == null || opdsFormatLabel(it) == format }
    return when (sort) {
        OpdsCatalogSort.MODIFIED -> filtered.sortedWith(
            compareByDescending<OpdsEntry> { opdsModifiedInstant(it.modifiedAt) }
                .thenBy { it.title.lowercase() }
        )
        OpdsCatalogSort.TITLE -> filtered.sortedBy { it.title.lowercase() }
    }
}

internal fun formatOpdsModifiedAt(value: String?, zoneId: ZoneId = ZoneId.systemDefault()): String {
    val instant = opdsModifiedInstant(value) ?: return "更新时间未知"
    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(zoneId)
        .format(instant)
}

internal fun opdsFormatLabel(entry: OpdsEntry): String {
    val type = entry.acquisitionLink?.type.orEmpty().lowercase()
    val extension = entry.acquisitionLink?.href.orEmpty()
        .substringBefore('?')
        .substringAfterLast('.', missingDelimiterValue = "")
        .uppercase()
    return when {
        "epub" in type -> "EPUB"
        "pdf" in type -> "PDF"
        "mobipocket" in type -> extension.takeIf { it == "MOBI" || it == "AZW3" } ?: "MOBI"
        "markdown" in type || extension == "MD" -> "Markdown"
        "html" in type -> "HTML"
        "text" in type || "txt" in type -> "TXT"
        extension.isNotBlank() -> extension
        type.isNotBlank() -> type.substringAfterLast('/').uppercase()
        else -> "未知格式"
    }
}

private fun opdsModifiedInstant(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    return runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant() }.getOrNull()
}
