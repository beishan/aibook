package com.aibook.android.core.data.repository

import com.aibook.android.core.data.prefs.ServerConfigStore
import com.aibook.android.core.network.api.ApiServiceFactory
import com.aibook.android.core.network.api.AuthApi
import com.aibook.android.core.network.api.AuthTokenProvider
import com.aibook.android.core.network.api.BookApi
import com.aibook.android.core.network.api.ReadingProgressApi
import com.aibook.android.core.network.api.ServerLibraryApi
import com.aibook.android.core.network.api.dto.AuthResponse
import com.aibook.android.core.network.api.dto.BookDTO
import com.aibook.android.core.network.api.dto.BookPage
import com.aibook.android.core.network.api.dto.LoginRequest
import com.aibook.android.core.network.api.dto.ProcessedContentResponse
import com.aibook.android.core.network.api.dto.RegisterRequest
import com.aibook.android.core.network.api.dto.SaveProgressRequest
import com.aibook.android.core.network.api.dto.ReadingProgressDTO
import com.aibook.android.core.network.api.dto.ShelfOverviewDTO
import com.aibook.android.core.network.api.dto.BookListDTO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.io.File

class ServerRepository(
    private val serverConfigStore: ServerConfigStore
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

    suspend fun setServerUrl(url: String) {
        serverConfigStore.setServerUrl(url)
        cachedServerUrl = url
        retrofit = null
        okHttpClient = null
        if (url.isNotBlank()) ensureRetrofit()
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

    suspend fun getBookById(id: Long): Result<BookDTO> {
        return runCatching { getBookApi().getBookById(id) }
    }

    suspend fun getProcessedContent(bookId: Long): Result<ProcessedContentResponse> {
        return runCatching { getBookApi().getProcessedContent(bookId) }
    }

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
            check(temporary.length() > 0L) { "服务端返回了空文件" }
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
        totalProgress: Int
    ): Result<Unit> {
        val request = SaveProgressRequest(chapter, chapterTitle, chapterProgress, totalProgress)
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
                bookId, chapter, chapterTitle, chapterProgress, totalProgress
            )
        }
        return result.map { }
    }

    suspend fun getReadingProgress(
        bookId: Long,
        versionId: Long? = null
    ): Result<ReadingProgressDTO> {
        flushPendingReadingProgress(bookId, versionId)
        return runCatching { getReadingProgressApi().getProgress(bookId, versionId) }
    }

    private suspend fun flushPendingReadingProgress(bookId: Long, versionId: Long?) {
        val pending = serverConfigStore.pendingReadingProgress(bookId) ?: return
        runCatching {
            getReadingProgressApi().saveProgress(
                bookId,
                versionId,
                SaveProgressRequest(
                    pending.chapter,
                    pending.chapterTitle,
                    pending.chapterProgress,
                    pending.totalProgress
                )
            )
        }.onSuccess { serverConfigStore.clearPendingReadingProgress(bookId) }
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
}
