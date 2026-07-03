package com.aibook.android.core.data.mapper

import com.aibook.android.core.data.db.BookEntity
import com.aibook.android.core.data.db.OpdsConnectionEntity
import com.aibook.android.core.model.BookFormat
import com.aibook.android.core.model.LocalBook
import com.aibook.android.core.model.ReadingProgress
import com.aibook.android.core.model.ReadingStatus
import com.aibook.android.core.network.opds.OpdsConnection
import com.aibook.android.core.network.opds.OpdsSyncState
import java.time.Instant

fun BookEntity.toDomain(): LocalBook {
    return LocalBook(
        id = id,
        title = title,
        author = author,
        format = runCatching { BookFormat.valueOf(format) }.getOrDefault(BookFormat.TXT),
        uri = uri,
        sha256 = sha256,
        coverUri = coverUri,
        status = runCatching { ReadingStatus.valueOf(status) }.getOrDefault(ReadingStatus.UNREAD),
        favorite = favorite,
        shelved = shelved,
        importedAt = Instant.ofEpochMilli(importedAt),
        lastReadAt = lastReadAt?.let { Instant.ofEpochMilli(it) },
        progress = ReadingProgress(
            chapterHref = progressChapterHref,
            chapterTitle = progressChapterTitle,
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
        format = format.name,
        uri = uri,
        sha256 = sha256,
        coverUri = coverUri,
        status = status.name,
        favorite = favorite,
        shelved = shelved,
        importedAt = importedAt.toEpochMilli(),
        lastReadAt = lastReadAt?.toEpochMilli(),
        progressPercent = progress.percent,
        progressChapterHref = progress.chapterHref,
        progressChapterTitle = progress.chapterTitle,
        progressPositionLabel = progress.positionLabel,
        source = "LOCAL"
    )
}

fun OpdsConnectionEntity.toDomain(): OpdsConnection {
    return OpdsConnection(
        id = id,
        name = name,
        baseUrl = baseUrl,
        username = username,
        password = password,
        enabled = enabled,
        lastSyncedAt = lastSyncedAt,
        bookCount = bookCount,
        syncState = runCatching { OpdsSyncState.valueOf(syncState) }.getOrDefault(OpdsSyncState.IDLE),
        lastErrorMessage = lastErrorMessage
    )
}

fun OpdsConnection.toEntity(): OpdsConnectionEntity {
    return OpdsConnectionEntity(
        id = id,
        name = name,
        baseUrl = baseUrl,
        username = username,
        password = password,
        enabled = enabled,
        lastSyncedAt = lastSyncedAt,
        bookCount = bookCount,
        syncState = syncState.name,
        lastErrorMessage = lastErrorMessage
    )
}
