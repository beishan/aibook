package com.aibook.android.core.network.api

import kotlinx.serialization.Serializable
import retrofit2.http.*

@Serializable
data class AudioTrackDto(val id: Long, val title: String, val durationMs: Long, val mimeType: String = "audio/mpeg", val fileSize: Long = 0, val fileHash: String = "")
@Serializable
data class AudioProgressDto(val revision: Long = 0, val trackId: Long? = null, val positionMs: Long = 0, val status: String = "NEW")
@Serializable
data class AudioSegmentDto(val trackId: Long, val startMs: Long, val endMs: Long)
@Serializable
data class AudioChapterDto(val id: Long, val title: String, val segments: List<AudioSegmentDto>)
@Serializable
data class AudioBookmarkDto(val id: Long = 0, val trackId: Long, val positionMs: Long, val title: String)
@Serializable
data class AudioEditionDto(
    val id: Long, val bookId: Long, val title: String, val name: String,
    val author: String? = null, val narrator: String = "", val durationMs: Long = 0,
    val contentRevision: Long = 1, val tracks: List<AudioTrackDto> = emptyList(),
    val chapters: List<AudioChapterDto> = emptyList(), val bookmarks: List<AudioBookmarkDto> = emptyList(),
    val progress: AudioProgressDto = AudioProgressDto()
)
@Serializable
data class AudioPageDto(val content: List<AudioEditionDto>, val totalElements: Long)
@Serializable
data class AudioSessionRequest(val takeover: Boolean = false)
@Serializable
data class AudioSessionDto(val sessionId: String, val key: String, val contentRevision: Long, val progress: AudioProgressDto)
@Serializable
data class AudioHeartbeatDto(
    val sequence: Long, val revision: Long, val contentRevision: Long, val trackId: Long,
    val positionMs: Long, val elapsedMs: Long, val finished: Boolean = false
)
@Serializable
data class AudioSettingsDto(val speed: Double = 1.0, val backSeconds: Int = 15, val forwardSeconds: Int = 30, val sleepMinutes: Int = 30)

interface AudioApi {
    @GET("api/audiobooks/{id}/offline-manifest")
    suspend fun manifest(@Path("id") id: Long): AudioEditionDto
    @Streaming
    @GET("api/audiobooks/{id}/tracks/{trackId}/download")
    suspend fun download(@Path("id") id: Long, @Path("trackId") trackId: Long,
        @Header("Range") range: String?): retrofit2.Response<okhttp3.ResponseBody>
    @POST("api/audiobooks/{id}/offline-progress")
    suspend fun offlineProgress(@Path("id") id: Long, @Body event: AudioOfflineProgressDto): AudioProgressDto

    @GET("api/audiobooks")
    suspend fun list(@Query("page") page: Int = 0, @Query("keyword") keyword: String = "", @Query("size") size: Int = 24): AudioPageDto
    @GET("api/audiobooks/{id}")
    suspend fun detail(@Path("id") id: Long): AudioEditionDto
    @POST("api/audiobooks/{id}/sessions")
    suspend fun start(@Path("id") id: Long, @Body request: AudioSessionRequest): AudioSessionDto
    @PUT("api/audiobooks/sessions/{id}")
    suspend fun heartbeat(@Path("id") id: String, @Body request: AudioHeartbeatDto): AudioProgressDto
    @DELETE("api/audiobooks/sessions/{id}")
    suspend fun end(@Path("id") id: String)
    @GET("api/audiobooks/settings")
    suspend fun settings(): AudioSettingsDto
    @PUT("api/audiobooks/settings")
    suspend fun settings(@Body settings: AudioSettingsDto)
    @POST("api/audiobooks/{id}/bookmarks")
    suspend fun bookmark(@Path("id") id: Long, @Body bookmark: AudioBookmarkDto)
}

@Serializable
data class AudioIntervalDto(val startMs: Long, val endMs: Long)
@Serializable
data class AudioOfflineProgressDto(
    val eventId: String, val baseRevision: Long, val contentRevision: Long,
    val trackId: Long, val positionMs: Long, val finished: Boolean = false,
    val resolveConflict: Boolean = false, val intervals: List<AudioIntervalDto> = emptyList()
)

object AudioJson {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    fun encode(edition: AudioEditionDto): String = json.encodeToString(AudioEditionDto.serializer(), edition)
    fun decode(value: String): AudioEditionDto = json.decodeFromString(AudioEditionDto.serializer(), value)
    fun encodeProgress(progress: AudioOfflineProgressDto): String = json.encodeToString(AudioOfflineProgressDto.serializer(), progress)
    fun decodeProgress(value: String): AudioOfflineProgressDto = json.decodeFromString(AudioOfflineProgressDto.serializer(), value)
}
