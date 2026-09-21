package com.aibook.android.core.data.repository

import com.aibook.android.core.data.prefs.ServerConfigStore
import com.aibook.android.core.network.api.ApiServiceFactory
import com.aibook.android.core.network.api.AuthApi
import com.aibook.android.core.network.api.AuthTokenProvider
import com.aibook.android.core.network.api.BookApi
import com.aibook.android.core.network.api.ReadingProgressApi
import com.aibook.android.core.network.api.ServerLibraryApi
import com.aibook.android.core.network.api.AnnotationApi
import com.aibook.android.core.network.api.dto.AuthResponse
import com.aibook.android.core.network.api.dto.BookDTO
import com.aibook.android.core.network.api.dto.BookPage
import com.aibook.android.core.network.api.dto.LoginRequest
import com.aibook.android.core.network.api.dto.ProcessedContentResponse
import com.aibook.android.core.network.api.dto.StructuredChapterDTO
import com.aibook.android.core.network.api.dto.StructuredManifestDTO
import com.aibook.android.core.network.api.dto.RegisterRequest
import com.aibook.android.core.network.api.dto.SaveProgressRequest
import com.aibook.android.core.network.api.dto.UpdateReadingTimeRequest
import com.aibook.android.core.network.api.dto.ReadingProgressDTO
import com.aibook.android.core.network.api.dto.ShelfOverviewDTO
import com.aibook.android.core.network.api.dto.BookListDTO
import com.aibook.android.core.network.api.dto.BookmarkDTO
import com.aibook.android.core.network.api.dto.CreateBookmarkRequest
import com.aibook.android.core.network.api.dto.CreateHighlightRequest
import com.aibook.android.core.network.api.dto.HighlightDTO
import com.aibook.android.core.reader.ReaderBookmark
import com.aibook.android.core.reader.ReaderHighlight
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.Retrofit
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.io.File
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.Instant
import org.json.JSONObject

class ServerRepository(
    private val serverConfigStore: ServerConfigStore,
    private val onPendingProgress: () -> Unit = {}
) : AuthTokenProvider {

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var cachedServerUrl: String = ""

    @Volatile
    private var retrofit: Retrofit? = null

    @Volatile
    private var okHttpClient: OkHttpClient? = null

    override fun token(): String? = cachedToken

    suspend fun initialize() {
        cachedToken = serverConfigStore.tokenSync()
        cachedServerUrl = serverConfigStore.serverUrl.first()
        if (cachedServerUrl.isNotBlank()) {
            ensureRetrofit()
        }
    }

    val serverUrl: Flow<String> = serverConfigStore.serverUrl
    val username: Flow<String?> = serverConfigStore.username
    val isLoggedIn: Flow<Boolean> = serverConfigStore.isLoggedIn
    val pendingProgressCount: Flow<Int> = serverConfigStore.pendingProgressCount
    val lastProgressSyncAt: Flow<Long?> = serverConfigStore.lastProgressSyncAt
    val lastProgressSyncError: Flow<String?> = serverConfigStore.lastProgressSyncError

    suspend fun structuredCacheNamespace(): String {
        val server = cachedServerUrl.ifBlank { serverConfigStore.serverUrl.first() }
        val account = serverConfigStore.username.first().orEmpty()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$server|$account".toByteArray(StandardCharsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    suspend fun setServerUrl(url: String): Result<String> = runCatching {
        val normalizedUrl = normalizeServerUrl(url)
        requireNotNull(normalizedUrl.toHttpUrlOrNull()) { "云端地址格式无效" }
        val previousUrl = cachedServerUrl.ifBlank { serverConfigStore.serverUrl.first().trim().trimEnd('/') }
        val serverChanged = previousUrl.isNotBlank() && previousUrl != normalizedUrl
        if (serverChanged) {
            cachedToken = null
            serverConfigStore.clearAllPendingReadingProgress()
            serverConfigStore.clearAuth()
        }
        serverConfigStore.setServerUrl(normalizedUrl)
        cachedServerUrl = normalizedUrl
        retrofit = null
        okHttpClient = null
        if (normalizedUrl.isNotBlank()) ensureRetrofit()
        normalizedUrl
    }

    suspend fun login(username: String, password: String): Result<AuthResponse> {
        return runCatching {
            val api = getAuthApi()
            val response = api.login(LoginRequest(username, password))
            cachedToken = response.token
            serverConfigStore.setAuth(response.token, response.username, response.email)
            response
        }
    }

    suspend fun register(
        username: String,
        email: String,
        password: String,
        nickname: String?
    ): Result<AuthResponse> {
        return runCatching {
            val api = getAuthApi()
            val response = api.register(RegisterRequest(username, email, password, nickname))
            cachedToken = response.token
            serverConfigStore.setAuth(response.token, response.username, response.email)
            response
        }
    }

    suspend fun logout() {
        cachedToken = null
        serverConfigStore.clearAllPendingReadingProgress()
        serverConfigStore.clearAuth()
    }

    suspend fun getBooks(page: Int = 0, size: Int = 20): Result<BookPage> {
        return runCatching { getBookApi().getBooks(page = page, size = size) }
    }

    suspend fun searchBooks(keyword: String, page: Int = 0): Result<BookPage> {
        return runCatching { getBookApi().searchBooks(keyword, page = page) }
    }

    suspend fun getFavoriteBooks(page: Int = 0): Result<BookPage> {
        return runCatching { getBookApi().getFavoriteBooks(page = page) }
    }

    suspend fun getAllBooks(pageSize: Int = 100): Result<List<BookDTO>> = runCatching {
        collectBookPages { page -> getBookApi().getBooks(page = page, size = pageSize) }
    }

    suspend fun getAllFavoriteBooks(pageSize: Int = 100): Result<List<BookDTO>> = runCatching {
        collectBookPages { page -> getBookApi().getFavoriteBooks(page = page, size = pageSize) }
    }

    suspend fun getBookById(id: Long): Result<BookDTO> {
        return runCatching { getBookApi().getBookById(id) }
    }

    suspend fun recordBookOpen(bookId: Long, versionId: Long? = null): Result<Unit> =
        runCatching { getBookApi().recordBookOpen(bookId, versionId) }

    suspend fun getProcessedContent(
        bookId: Long,
        versionId: Long? = null
    ): Result<ProcessedContentResponse> {
        return runCatching { getBookApi().getProcessedContent(bookId, versionId) }
    }

    suspend fun getStructuredManifest(
        bookId: Long,
        versionId: Long? = null
    ): Result<StructuredManifestDTO> =
        runCatching { getBookApi().getStructuredManifest(bookId, versionId) }

    suspend fun getStructuredChapter(
        bookId: Long,
        chapterId: Long,
        versionId: Long? = null
    ): Result<StructuredChapterDTO> =
        runCatching { getBookApi().getStructuredChapter(bookId, chapterId, versionId) }

    suspend fun downloadBookContent(
        bookId: Long,
        target: File,
        versionId: Long? = null
    ): Result<File> = runCatching {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "${target.name}.part")
        try {
            getBookApi().getBookContent(bookId, versionId).use { body ->
                body.byteStream().use { input ->
                    temporary.outputStream().buffered().use { output -> input.copyTo(output) }
                }
            }
            check(temporary.length() > 0L) { "云端返回了空文件" }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
        target
    }

    suspend fun saveReadingProgress(
        bookId: Long,
        versionId: Long? = null,
        chapter: String?,
        chapterTitle: String? = null,
        chapterProgress: Int,
        totalProgress: Int,
        locator: String? = null
    ): Result<Unit> {
        val request = SaveProgressRequest(chapter, chapterTitle, chapterProgress, totalProgress, locator)
        val result = runCatching {
            getReadingProgressApi().saveProgress(
                bookId,
                versionId,
                request
            )
        }
        if (result.isSuccess) {
            serverConfigStore.clearPendingReadingProgress(bookId)
        } else {
            serverConfigStore.savePendingReadingProgress(
                bookId, versionId, chapter, chapterTitle, chapterProgress, totalProgress, locator
            )
            onPendingProgress()
        }
        return result.map { }
    }

    suspend fun getReadingProgress(
        bookId: Long,
        versionId: Long? = null
    ): Result<ReadingProgressDTO> {
        val remote = runCatching { getReadingProgressApi().getProgress(bookId, versionId) }
        val current = remote.getOrNull() ?: return remote
        val pending = serverConfigStore.pendingReadingProgress(bookId) ?: return remote
        val remoteUpdatedAt = current.updatedAt.toEpochMillisOrNull()
        if (remoteUpdatedAt != null && pending.savedAtEpochMillis < remoteUpdatedAt) {
            serverConfigStore.clearPendingReadingProgress(bookId)
            return remote
        }
        val flushed = flushPendingReadingProgress(bookId, versionId, pending)
        return if (flushed.isSuccess) flushed else remote
    }

    suspend fun addReadingTime(
        bookId: Long,
        seconds: Long,
        versionId: Long? = null
    ): Result<Unit> = runCatching {
        if (seconds > 0) {
            getReadingProgressApi().updateReadingTime(
                bookId,
                versionId,
                UpdateReadingTimeRequest(seconds)
            )
        }
    }.map { }

    private suspend fun flushPendingReadingProgress(
        bookId: Long,
        versionId: Long?,
        pending: com.aibook.android.core.data.prefs.PendingReadingProgress
    ): Result<ReadingProgressDTO> = runCatching {
            getReadingProgressApi().saveProgress(
                bookId,
                versionId,
                SaveProgressRequest(
                    pending.chapter,
                    pending.chapterTitle,
                    pending.chapterProgress,
                    pending.totalProgress,
                    pending.locator
                )
            )
        }.onSuccess { serverConfigStore.clearPendingReadingProgress(bookId) }

    suspend fun syncPendingReadingProgress(): ProgressSyncResult {
        val pendingItems = serverConfigStore.pendingReadingProgresses()
        if (pendingItems.isEmpty()) {
            serverConfigStore.markProgressSyncSuccess()
            return ProgressSyncResult(0, 0, 0)
        }
        var synced = 0
        var failed = 0
        pendingItems.forEach { pending ->
            val result = getReadingProgress(pending.bookId, pending.versionId)
            if (result.isSuccess && serverConfigStore.pendingReadingProgress(pending.bookId) == null) {
                synced += 1
            } else {
                failed += 1
            }
        }
        val remaining = serverConfigStore.pendingReadingProgresses().size
        if (failed == 0) {
            serverConfigStore.markProgressSyncSuccess()
        } else {
            serverConfigStore.markProgressSyncFailure("$failed 项阅读进度暂未同步")
        }
        return ProgressSyncResult(synced, failed, remaining)
    }

    suspend fun getShelf(): Result<ShelfOverviewDTO> =
        runCatching { getServerLibraryApi().getShelf() }

    suspend fun addToShelf(bookId: Long): Result<BookDTO> =
        runCatching { getServerLibraryApi().addToShelf(bookId) }

    suspend fun removeFromShelf(bookId: Long): Result<BookDTO> =
        runCatching { getServerLibraryApi().removeFromShelf(bookId) }

    suspend fun toggleFavorite(bookId: Long): Result<BookDTO> =
        runCatching { getServerLibraryApi().toggleFavorite(bookId) }

    suspend fun getBookLists(): Result<List<BookListDTO>> =
        runCatching { getServerLibraryApi().getBookLists() }

    suspend fun getBookList(listId: Long): Result<BookListDTO> =
        runCatching { getServerLibraryApi().getBookList(listId) }

    suspend fun createBookList(name: String, description: String): Result<BookListDTO> =
        runCatching {
            getServerLibraryApi().createBookList(mapOf("name" to name, "description" to description))
        }

    suspend fun updateBookList(listId: Long, name: String, description: String): Result<BookListDTO> =
        runCatching {
            getServerLibraryApi().updateBookList(
                listId,
                mapOf("name" to name, "description" to description)
            )
        }

    suspend fun deleteBookList(listId: Long): Result<Unit> =
        runCatching { getServerLibraryApi().deleteBookList(listId) }

    suspend fun getBookmarks(bookId: Long): Result<List<ReaderBookmark>> = runCatching {
        getAnnotationApi().getBookmarks(bookId).map { it.toReaderBookmark(bookId) }
    }

    suspend fun createBookmark(bookId: Long, bookmark: ReaderBookmark): Result<ReaderBookmark> = runCatching {
        getAnnotationApi().createBookmark(
            bookId,
            CreateBookmarkRequest(
                title = bookmark.chapterTitle ?: "书签",
                excerpt = bookmark.chapterTitle,
                chapter = bookmark.chapterTitle,
                chapterIndex = bookmark.chapterIndex?.plus(1),
                cfi = bookmarkLocator(bookmark),
                scrollPosition = bookmark.scrollOffset.toLong(),
                page = 1
            )
        ).toReaderBookmark(bookId)
    }

    suspend fun deleteBookmark(bookId: Long, bookmarkId: String): Result<Unit> = runCatching {
        getAnnotationApi().deleteBookmark(bookId, bookmarkId.serverAnnotationId())
    }

    suspend fun getHighlights(bookId: Long): Result<List<ReaderHighlight>> = runCatching {
        getAnnotationApi().getHighlights(bookId).map { it.toReaderHighlight(bookId) }
    }

    suspend fun createHighlight(bookId: Long, highlight: ReaderHighlight): Result<ReaderHighlight> = runCatching {
        getAnnotationApi().createHighlight(
            bookId,
            CreateHighlightRequest(
                cfiRange = highlightLocator(highlight),
                text = highlight.excerpt,
                color = String.format("#%06X", highlight.color and 0xFFFFFF),
                chapter = highlight.chapterHref,
                note = highlight.note
            )
        ).toReaderHighlight(bookId)
    }

    suspend fun deleteHighlight(bookId: Long, highlightId: String): Result<Unit> = runCatching {
        getAnnotationApi().deleteHighlight(bookId, highlightId.serverAnnotationId())
    }

    fun resolveCoverUrl(coverUrl: String?): String? {
        if (coverUrl.isNullOrBlank()) return null
        val baseUrl = cachedServerUrl.trimEnd('/')
        if (baseUrl.isBlank()) return coverUrl
        return when {
            coverUrl.startsWith("covers/") -> "$baseUrl/api/covers/${coverUrl.removePrefix("covers/")}"
            coverUrl.startsWith("http://") || coverUrl.startsWith("https://") -> {
                val encoded = URLEncoder.encode(coverUrl, StandardCharsets.UTF_8.toString())
                "$baseUrl/api/covers/proxy?url=$encoded"
            }
            coverUrl.startsWith("/") -> "$baseUrl$coverUrl"
            else -> "$baseUrl/$coverUrl"
        }
    }

    private suspend fun ensureRetrofit(): Retrofit {
        retrofit?.let { return it }

        // Application 初始化与首屏请求可能并发，确保第一次真实请求已经拿到持久化 JWT。
        if (cachedToken == null) {
            cachedToken = serverConfigStore.tokenSync()
        }

        val url = cachedServerUrl.ifBlank {
            serverConfigStore.serverUrl.first()
        }

        synchronized(this) {
            retrofit?.let { return it }

            val client = okHttpClient ?: ApiServiceFactory.createOkHttpClient(this).also {
                okHttpClient = it
            }

            cachedServerUrl = url

            return ApiServiceFactory.createRetrofit(url, client).also {
                retrofit = it
            }
        }
    }

    private fun normalizeServerUrl(url: String): String {
        val trimmed = url.trim().trimEnd('/')
        if (trimmed.isBlank()) return ""
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }

    private suspend fun getAuthApi(): AuthApi {
        return ApiServiceFactory.createAuthApi(ensureRetrofit())
    }

    private suspend fun getBookApi(): BookApi {
        return ApiServiceFactory.createBookApi(ensureRetrofit())
    }

    private suspend fun getReadingProgressApi(): ReadingProgressApi {
        return ApiServiceFactory.createReadingProgressApi(ensureRetrofit())
    }

    private suspend fun getServerLibraryApi(): ServerLibraryApi {
        return ApiServiceFactory.createServerLibraryApi(ensureRetrofit())
    }

    private suspend fun getAnnotationApi(): AnnotationApi =
        ApiServiceFactory.createAnnotationApi(ensureRetrofit())

    private suspend fun collectBookPages(loader: suspend (Int) -> BookPage): List<BookDTO> {
        val books = mutableListOf<BookDTO>()
        var pageNumber = 0
        do {
            val page = loader(pageNumber)
            books += page.content
            pageNumber += 1
        } while (!page.last && pageNumber < page.totalPages)
        return books
    }

    private fun String?.toEpochMillisOrNull(): Long? {
        if (this.isNullOrBlank()) return null
        return runCatching {
            LocalDateTime.parse(this).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
    }

    private fun BookmarkDTO.toReaderBookmark(bookId: Long): ReaderBookmark {
        val locator = cfi.jsonObjectOrNull()
        return ReaderBookmark(
            id = "server:$id",
            bookId = "server:$bookId",
            chapterHref = locator?.optString("href")?.takeIf(String::isNotBlank),
            chapterTitle = chapter,
            progress = locator?.optDouble("progress", 0.0)?.toFloat()?.coerceIn(0f, 1f) ?: 0f,
            chapterIndex = locator?.optInt("chapterIndex")?.takeIf { locator.has("chapterIndex") }
                ?: chapterIndex?.minus(1)?.coerceAtLeast(0),
            lineIndex = locator?.optInt("lineIndex", 0)?.coerceAtLeast(0) ?: 0,
            scrollOffset = locator?.optInt("scrollOffset", scrollPosition?.toInt() ?: 0)?.coerceAtLeast(0) ?: 0,
            createdAt = createdAt.toInstantOrNow()
        )
    }

    private fun HighlightDTO.toReaderHighlight(bookId: Long): ReaderHighlight {
        val locator = cfiRange.jsonObjectOrNull()
        return ReaderHighlight(
            id = "server:$id",
            bookId = "server:$bookId",
            chapterHref = locator?.optString("href")?.takeIf(String::isNotBlank) ?: chapter,
            chapterIndex = locator?.optInt("chapterIndex")?.takeIf { locator.has("chapterIndex") },
            lineIndex = locator?.optInt("lineIndex", 0)?.coerceAtLeast(0) ?: 0,
            startOffset = locator?.optInt("startOffset", 0)?.coerceAtLeast(0) ?: 0,
            endOffset = locator?.optInt("endOffset", text.length)?.coerceIn(0, text.length) ?: text.length,
            excerpt = text,
            note = note,
            color = color.removePrefix("#").toLongOrNull(16)?.let { 0xFF000000 or it } ?: 0xFFFFE082,
            createdAt = createdAt.toInstantOrNow()
        )
    }

    private fun bookmarkLocator(bookmark: ReaderBookmark): String = JSONObject()
        .put("href", bookmark.chapterHref)
        .put("chapterIndex", bookmark.chapterIndex)
        .put("lineIndex", bookmark.lineIndex)
        .put("scrollOffset", bookmark.scrollOffset)
        .put("progress", bookmark.progress)
        .toString()

    private fun highlightLocator(highlight: ReaderHighlight): String = JSONObject()
        .put("href", highlight.chapterHref)
        .put("chapterIndex", highlight.chapterIndex)
        .put("lineIndex", highlight.lineIndex)
        .put("startOffset", highlight.startOffset)
        .put("endOffset", highlight.endOffset)
        .toString()

    private fun String?.jsonObjectOrNull(): JSONObject? =
        this?.let { runCatching { JSONObject(it) }.getOrNull() }

    private fun String?.toInstantOrNow(): Instant = this?.let {
        runCatching { LocalDateTime.parse(it).atZone(ZoneId.systemDefault()).toInstant() }.getOrNull()
    } ?: Instant.now()

    private fun String.serverAnnotationId(): Long =
        removePrefix("server:").toLongOrNull() ?: error("云端标注 ID 无效")
}

data class ProgressSyncResult(
    val synced: Int,
    val failed: Int,
    val remaining: Int
)
