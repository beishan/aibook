package com.aibook.android.core.network.api

import com.aibook.android.core.network.api.dto.ReadingProgressDTO
import com.aibook.android.core.network.api.dto.SaveProgressRequest
import com.aibook.android.core.network.api.dto.UpdateReadingTimeRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ReadingProgressApi {
    @GET("api/reading-progress/book/{bookId}")
    suspend fun getProgress(
        @Path("bookId") bookId: Long,
        @Query("versionId") versionId: Long? = null
    ): ReadingProgressDTO

    @POST("api/reading-progress/book/{bookId}")
    suspend fun saveProgress(
        @Path("bookId") bookId: Long,
        @Query("versionId") versionId: Long? = null,
        @Body request: SaveProgressRequest
    ): ReadingProgressDTO

    @PUT("api/reading-progress/book/{bookId}/time")
    suspend fun updateReadingTime(
        @Path("bookId") bookId: Long,
        @Query("versionId") versionId: Long? = null,
        @Body request: UpdateReadingTimeRequest
    ): ReadingProgressDTO
}
