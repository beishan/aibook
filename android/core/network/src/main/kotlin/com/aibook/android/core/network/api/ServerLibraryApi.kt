package com.aibook.android.core.network.api

import com.aibook.android.core.network.api.dto.BookDTO
import com.aibook.android.core.network.api.dto.BookListDTO
import com.aibook.android.core.network.api.dto.ShelfOverviewDTO
import retrofit2.http.DELETE
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface ServerLibraryApi {
    @GET("api/shelf")
    suspend fun getShelf(): ShelfOverviewDTO

    @POST("api/shelf/books/{bookId}")
    suspend fun addToShelf(@Path("bookId") bookId: Long): BookDTO

    @DELETE("api/shelf/books/{bookId}")
    suspend fun removeFromShelf(@Path("bookId") bookId: Long): BookDTO

    @PUT("api/books/{bookId}/favorite")
    suspend fun toggleFavorite(@Path("bookId") bookId: Long): BookDTO

    @GET("api/booklists")
    suspend fun getBookLists(): List<BookListDTO>

    @GET("api/booklists/{listId}")
    suspend fun getBookList(@Path("listId") listId: Long): BookListDTO

    @POST("api/booklists")
    suspend fun createBookList(@Body body: Map<String, String>): BookListDTO

    @PUT("api/booklists/{listId}")
    suspend fun updateBookList(
        @Path("listId") listId: Long,
        @Body body: Map<String, String>
    ): BookListDTO

    @DELETE("api/booklists/{listId}")
    suspend fun deleteBookList(@Path("listId") listId: Long)

    @POST("api/booklists/{listId}/books/{bookId}")
    suspend fun addBookToList(
        @Path("listId") listId: Long,
        @Path("bookId") bookId: Long
    ): BookListDTO

    @DELETE("api/booklists/{listId}/books/{bookId}")
    suspend fun removeBookFromList(
        @Path("listId") listId: Long,
        @Path("bookId") bookId: Long
    ): BookListDTO
}
