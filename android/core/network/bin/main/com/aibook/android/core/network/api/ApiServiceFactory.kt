package com.aibook.android.core.network.api

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object ApiServiceFactory {

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun createOkHttpClient(
        tokenProvider: AuthTokenProvider,
        enableLogging: Boolean = false
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(tokenProvider))

        if (enableLogging) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
            )
        }

        return builder.build()
    }

    fun createRetrofit(
        baseUrl: String,
        client: OkHttpClient
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(baseUrl.ensureTrailingSlash())
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    fun createAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)
    fun createBookApi(retrofit: Retrofit): BookApi = retrofit.create(BookApi::class.java)
    fun createReadingProgressApi(retrofit: Retrofit): ReadingProgressApi =
        retrofit.create(ReadingProgressApi::class.java)

    private fun String.ensureTrailingSlash(): String =
        if (endsWith('/')) this else "$this/"
}
