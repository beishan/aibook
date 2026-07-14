package com.aibook.android.core.reader

import com.aibook.android.core.model.BookFormat
import java.io.File

data class BookContentRequest(
    val bookId: String,
    val file: File,
    val format: BookFormat,
    val contentHash: String? = null,
    val preferredChapterHref: String? = null,
    val cacheDirectory: File
)

data class ReaderBookContent(
    val title: String? = null,
    val author: String? = null,
    val coverPath: String? = null,
    val chapters: List<ReaderChapter>
)

sealed interface BookContentError {
    data object FileMissing : BookContentError
    data object PermissionLost : BookContentError
    data object DrmProtected : BookContentError
    data object PasswordProtected : BookContentError
    data object UnsupportedVariant : BookContentError
    data object CorruptedFile : BookContentError
    data object InsufficientStorage : BookContentError
    data class ParseFailed(val safeMessage: String) : BookContentError
}

sealed interface BookContentResult {
    data class Success(val content: ReaderBookContent) : BookContentResult
    data class Failure(val error: BookContentError) : BookContentResult
}

interface BookContentLoader {
    val supportedFormats: Set<BookFormat>

    suspend fun load(request: BookContentRequest): BookContentResult
}
