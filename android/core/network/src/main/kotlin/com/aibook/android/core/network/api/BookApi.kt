package com.aibook.android.core.network.api

import com.aibook.android.core.network.api.dto.BookDTO
import com.aibook.android.core.network.api.dto.BookPage
import com.aibook.android.core.network.api.dto.ProcessedContentResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface BookApi {
    @GET("api/books")
    suspend fun getBooks(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("sortBy") sortBy: String = "createdAt",
        @Query("sortDir") sortDir: String = "desc",
        @Query("format") format: String? = null,
        @Query("status") status: String? = null
    ): BookPage

    @GET("api/books/search")
    suspend fun searchBooks(
        @Query("keyword") keyword: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): BookPage

    @GET("api/books/favorites")
    suspend fun getFavoriteBooks(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): BookPage

    @GET("api/books/{id}")
    suspend fun getBookById(@Path("id") id: Long): BookDTO

    @GET("api/books/{id}/content-processed")
    suspend fun getProcessedContent(@Path("id") id: Long): ProcessedContentResponse
}
