package com.aibook.android.core.data.repository

import com.aibook.android.core.data.prefs.ServerConfigStore
import com.aibook.android.core.network.api.ApiServiceFactory
import com.aibook.android.core.network.api.AuthApi
import com.aibook.android.core.network.api.AuthTokenProvider
import com.aibook.android.core.network.api.BookApi
import com.aibook.android.core.network.api.ReadingProgressApi
import com.aibook.android.core.network.api.dto.AuthResponse
import com.aibook.android.core.network.api.dto.BookDTO
import com.aibook.android.core.network.api.dto.BookPage
import com.aibook.android.core.network.api.dto.LoginRequest
import com.aibook.android.core.network.api.dto.ProcessedContentResponse
import com.aibook.android.core.network.api.dto.RegisterRequest
import com.aibook.android.core.network.api.dto.SaveProgressRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import retrofit2.Retrofit

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

    suspend fun saveReadingProgress(
        bookId: Long,
        chapter: String?,
        chapterProgress: Int,
        totalProgress: Int
    ): Result<Unit> {
        return runCatching {
            getReadingProgressApi().saveProgress(
                bookId,
                SaveProgressRequest(chapter, chapterProgress, totalProgress)
            )
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
}
