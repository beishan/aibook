package com.aibook.android.feature.reader

import com.aibook.android.core.network.api.dto.BookVersionDTO

internal fun resolveReaderVersion(
    versions: List<BookVersionDTO>,
    requestedVersionId: Long? = null
): BookVersionDTO {
    if (requestedVersionId != null) {
        return versions.firstOrNull { it.id == requestedVersionId }
            ?: error("所选版本已不可用，请重新选择")
    }
    return versions.firstOrNull { it.selected }
        ?: versions.firstOrNull { it.primaryVersion }
        ?: versions.firstOrNull()
        ?: error("书籍没有可阅读版本")
}

internal fun requiresReaderVersionSelection(
    versions: List<BookVersionDTO>,
    openedVersionId: Long?
): Boolean = (versions.size > 1 && openedVersionId == null) ||
    (openedVersionId != null && versions.none { it.id == openedVersionId })
