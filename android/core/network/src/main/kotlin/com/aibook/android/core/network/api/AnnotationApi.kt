package com.aibook.android.core.network.api

import com.aibook.android.core.network.api.dto.BookmarkDTO
import com.aibook.android.core.network.api.dto.CreateBookmarkRequest
import com.aibook.android.core.network.api.dto.CreateHighlightRequest
import com.aibook.android.core.network.api.dto.HighlightDTO
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface AnnotationApi {
    @GET("api/books/{bookId}/bookmarks")
    suspend fun getBookmarks(@Path("bookId") bookId: Long): List<BookmarkDTO>

    @POST("api/books/{bookId}/bookmarks")
    suspend fun createBookmark(
        @Path("bookId") bookId: Long,
        @Body request: CreateBookmarkRequest
    ): BookmarkDTO

    @DELETE("api/books/{bookId}/bookmarks/{bookmarkId}")
    suspend fun deleteBookmark(@Path("bookId") bookId: Long, @Path("bookmarkId") bookmarkId: Long)

    @GET("api/books/{bookId}/highlights")
    suspend fun getHighlights(@Path("bookId") bookId: Long): List<HighlightDTO>

    @POST("api/books/{bookId}/highlights")
    suspend fun createHighlight(
        @Path("bookId") bookId: Long,
        @Body request: CreateHighlightRequest
    ): HighlightDTO

    @DELETE("api/books/{bookId}/highlights/{highlightId}")
    suspend fun deleteHighlight(@Path("bookId") bookId: Long, @Path("highlightId") highlightId: Long)
}
