package com.aibook.android.core.data.mapper

import com.aibook.android.core.data.db.BookEntity
import com.aibook.android.core.data.db.ShelfFolderEntity
import com.aibook.android.core.model.BookFormat
import com.aibook.android.core.model.LocalBook
import com.aibook.android.core.model.ReadingProgress
import com.aibook.android.core.model.ReadingStatus
import com.aibook.android.core.model.ShelfFolder
import java.time.Instant

fun BookEntity.toDomain(): LocalBook {
    return LocalBook(
        id = id,
        title = title,
        author = author,
        description = description,
        rating = rating,
        tags = tags.split('|').map(String::trim).filter(String::isNotBlank).distinct(),
        format = runCatching { BookFormat.valueOf(format) }.getOrDefault(BookFormat.TXT),
        uri = uri,
        sha256 = sha256,
        coverUri = coverUri,
        folderId = folderId,
        status = runCatching { ReadingStatus.valueOf(status) }.getOrDefault(ReadingStatus.UNREAD),
        favorite = favorite,
        shelved = shelved,
        visibleInStore = visibleInStore,
        importedAt = Instant.ofEpochMilli(importedAt),
        lastReadAt = lastReadAt?.let { Instant.ofEpochMilli(it) },
        readingDurationSeconds = readingDurationSeconds,
        progress = ReadingProgress(
            chapterHref = progressChapterHref,
            chapterTitle = progressChapterTitle,
            chapterIndex = progressChapterIndex,
            lineIndex = progressLineIndex,
            scrollOffset = progressScrollOffset,
            pdfZoom = progressPdfZoom,
            percent = progressPercent,
            positionLabel = progressPositionLabel
        )
    )
}

fun LocalBook.toEntity(): BookEntity {
    return BookEntity(
        id = id,
        title = title,
        author = author,
        description = description,
        rating = rating,
        tags = tags.map { it.trim().replace("|", "") }.filter { it.isNotBlank() }.distinct().joinToString("|"),
        format = format.name,
        uri = uri,
        sha256 = sha256,
        coverUri = coverUri,
        folderId = folderId,
        status = status.name,
        favorite = favorite,
        shelved = shelved,
        visibleInStore = visibleInStore,
        importedAt = importedAt.toEpochMilli(),
        lastReadAt = lastReadAt?.toEpochMilli(),
        readingDurationSeconds = readingDurationSeconds,
        progressPercent = progress.percent,
        progressChapterHref = progress.chapterHref,
        progressChapterTitle = progress.chapterTitle,
        progressChapterIndex = progress.chapterIndex,
        progressLineIndex = progress.lineIndex,
        progressScrollOffset = progress.scrollOffset,
        progressPdfZoom = progress.pdfZoom,
        progressPositionLabel = progress.positionLabel,
        source = "LOCAL"
    )
}

fun ShelfFolderEntity.toDomain(): ShelfFolder {
    return ShelfFolder(
        id = id,
        name = name,
        createdAtEpochMillis = createdAt
    )
}

fun ShelfFolder.toEntity(): ShelfFolderEntity {
    return ShelfFolderEntity(
        id = id,
        name = name,
        createdAt = createdAtEpochMillis
    )
}
