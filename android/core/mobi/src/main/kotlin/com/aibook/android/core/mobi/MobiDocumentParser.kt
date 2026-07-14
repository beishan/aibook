package com.aibook.android.core.mobi

data class MobiDocument(
    val title: String? = null,
    val author: String? = null,
    val description: String? = null,
    val coverPath: String? = null,
    val chapters: List<MobiChapter> = emptyList()
)

data class MobiChapter(
    val title: String?,
    val href: String,
    val htmlPath: String
)

enum class MobiParseError {
    FILE_MISSING,
    DRM_PROTECTED,
    UNSUPPORTED_VARIANT,
    CORRUPTED_FILE,
    INSUFFICIENT_STORAGE,
    PARSE_FAILED
}

sealed interface MobiParseResult {
    data class Success(val document: MobiDocument) : MobiParseResult
    data class Failure(val error: MobiParseError) : MobiParseResult
}

fun interface MobiDocumentParser {
    suspend fun parse(filePath: String, outputDirectory: String): MobiParseResult
}

object NativeMobiStatus {
    const val SUCCESS = 0
    const val FILE_MISSING = 1
    const val DRM = 2
    const val UNSUPPORTED = 3
    const val CORRUPTED = 4
    const val NO_SPACE = 5

    fun toError(status: Int): MobiParseError = when (status) {
        FILE_MISSING -> MobiParseError.FILE_MISSING
        DRM -> MobiParseError.DRM_PROTECTED
        UNSUPPORTED -> MobiParseError.UNSUPPORTED_VARIANT
        CORRUPTED -> MobiParseError.CORRUPTED_FILE
        NO_SPACE -> MobiParseError.INSUFFICIENT_STORAGE
        else -> MobiParseError.PARSE_FAILED
    }
}
