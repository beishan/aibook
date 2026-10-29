package com.aibook.android.feature.audio

import android.content.Intent
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.aibook.android.core.network.api.*
import com.aibook.android.di.ServiceLocator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Owns playback even when no Compose page is attached. */
class AudioPlaybackService : MediaSessionService() {
    companion object {
        var active: AudioPlaybackService? = null
            private set
        private val mutableState = MutableStateFlow(AudioUiState())
        val state = mutableState.asStateFlow()
    }
    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private var api: AudioApi? = null
    private var session: AudioSessionDto? = null
    private var detail: AudioEditionDto? = null
    private var settings = AudioSettingsDto()
    private var revision = 0L
    private var sequence = 0L
    private var elapsed = 0L
    private var lastTick = 0L
    private var sleepAt: Long? = null
    private var accountToken: String? = null
    private var baseUrl = ""
    private var opening = false
    private var offlineEvent: AudioOfflineProgressDto? = null
    private var offlineStore: AudioOfflineStore? = null

    override fun onCreate() {
        super.onCreate()
        active = this
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(15000)
            .setSeekForwardIncrementMs(30000)
            .build()
        mediaSession = MediaSession.Builder(this, player).build()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                publish()
                if (!isPlaying) scope.launch { save() }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                mutableState.value = mutableState.value.copy(error = "音频中断，请重新打开收听：${error.errorCodeName}")
                scope.launch { save() }
            }
        })
        scope.launch {
            var ticks = 0
            lastTick = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(1000)
                val now = SystemClock.elapsedRealtime()
                val repository = ServiceLocator.get(this@AudioPlaybackService).serverRepository
                if ((session != null || offlineEvent != null) && (repository.token() != accountToken || repository.audioBaseUrl() != baseUrl)) {
                    player.pause(); player.clearMediaItems(); session = null; offlineEvent = null; detail = null
                    mutableState.value = AudioUiState(error = "账户或服务器已切换，收听已停止")
                }
                if (player.isPlaying) {
                    val duration = (now - lastTick).coerceIn(0, 30000)
                    elapsed += duration
                    offlineEvent?.let { event ->
                        val end = System.currentTimeMillis()
                        val intervals = event.intervals.toMutableList()
                        val previous = intervals.lastOrNull()
                        if (previous != null && previous.endMs >= end - duration - 1000) {
                            intervals[intervals.lastIndex] = previous.copy(endMs = end)
                        } else intervals.add(AudioIntervalDto(end-duration,end))
                        if (intervals.size > 1000) {
                            player.pause()
                            mutableState.value = mutableState.value.copy(error = "离线记录较多，请联网同步后继续")
                        } else offlineEvent = event.copy(intervals = intervals)
                    }
                }
                lastTick = now
                if (sleepAt?.let { now >= it } == true) { player.pause(); sleepAt = null }
                publish()
                if (++ticks % 10 == 0 && player.isPlaying) save()
            }
        }
    }

    fun open(id: Long, takeover: Boolean = false) {
        if (opening) return
        scope.launch {
            opening = true
            try {
                save()
                player.pause()
                session?.let { runCatching { api?.end(it.sessionId) } }
                session = null; offlineEvent = null; offlineStore = null
                val repository = ServiceLocator.get(this@AudioPlaybackService).serverRepository
                api = repository.audioApi()
                baseUrl = repository.audioBaseUrl()
                accountToken = repository.token()
                val edition = api!!.detail(id)
                val preferences = api!!.settings()
                val response = api!!.start(id, AudioSessionRequest(takeover))
                if (accountToken != repository.token() || baseUrl != repository.audioBaseUrl()) return@launch
                detail = edition; settings = preferences; session = response
                revision = response.progress.revision; sequence = 0; elapsed = 0
                sleepAt = null
                val items = edition.tracks.map { track ->
                    MediaItem.Builder().setMediaId(track.id.toString())
                        .setUri("$baseUrl/api/audio-media/${response.sessionId}/${track.id}?key=${response.key}")
                        .setMimeType(track.mimeType)
                        .setMediaMetadata(MediaMetadata.Builder().setTitle("${edition.title} · ${track.title}").setArtist(edition.narrator).build()).build()
                }
                val index = edition.tracks.indexOfFirst { it.id == response.progress.trackId }.coerceAtLeast(0)
                player.setMediaItems(items, index, response.progress.positionMs)
                player.setPlaybackSpeed(preferences.speed.toFloat())
                player.prepare(); player.play()
                mutableState.value = AudioUiState(edition = edition)
                publish()
            } catch (failure: Exception) {
                player.pause()
                val conflict = failure is retrofit2.HttpException && failure.code() == 409
                mutableState.value = mutableState.value.copy(error = if (conflict) "另一收听会话仍有效，确认后可接管。" else "无法开始收听：${failure.message}", conflictId = if (conflict) id else null)
            } finally { opening = false }
        }
    }

    private suspend fun save() = mutex.withLock {
        offlineEvent?.let { event ->
            val edition = detail ?: return@withLock
            val track = currentTrack() ?: return@withLock
            val updated = event.copy(trackId = track.id, positionMs = player.currentPosition.coerceIn(0,track.durationMs), finished = player.playbackState == Player.STATE_ENDED)
            offlineEvent = updated
            try { offlineStore?.save(edition.id,updated) }
            catch (_: Exception) { player.pause(); mutableState.value = mutableState.value.copy(error = "离线进度未能保存，已暂停") }
            return@withLock
        }
        val current = session ?: return@withLock
        val track = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return@withLock
        try {
            val result = api!!.heartbeat(current.sessionId, AudioHeartbeatDto(sequence++, revision,
                current.contentRevision, track, player.currentPosition.coerceAtLeast(0), elapsed,
                player.playbackState == Player.STATE_ENDED))
            if (session === current) revision = result.revision
        } catch (failure: Exception) {
            if (session === current) {
                session = null
                player.pause()
                mutableState.value = mutableState.value.copy(error = "进度同步失败，已暂停；请重新打开收听。")
            }
        }
    }

    fun openOffline(edition: AudioEditionDto) {
        if (opening) return
        scope.launch {
            opening = true
            try {
                player.pause(); save()
                session?.let { runCatching { api?.end(it.sessionId) } }
                session = null
                val repository = ServiceLocator.get(this@AudioPlaybackService).serverRepository
                accountToken = repository.token(); baseUrl = repository.audioBaseUrl(); api = repository.audioApi()
                val cache = AudioOfflineStore(this@AudioPlaybackService)
                val pending = cache.progress(edition.id)
                val first = edition.tracks.first()
                offlineEvent = pending?.copy(eventId = java.util.UUID.randomUUID().toString()) ?: AudioOfflineProgressDto(java.util.UUID.randomUUID().toString(),
                    edition.progress.revision,edition.contentRevision,edition.progress.trackId ?: first.id,edition.progress.positionMs)
                offlineStore = cache; detail = edition; elapsed = 0; sleepAt = null
                val items = edition.tracks.map { track ->
                    val file = cache.file(edition.id,track.id)
                    require(file.isFile && file.length() == track.fileSize) { "离线音轨缺失，请重新下载" }
                    MediaItem.Builder().setMediaId(track.id.toString()).setUri(android.net.Uri.fromFile(file))
                        .setMediaMetadata(MediaMetadata.Builder().setTitle("${edition.title} · ${track.title}").setArtist(edition.narrator).build()).build()
                }
                val index = edition.tracks.indexOfFirst { it.id == offlineEvent?.trackId }.coerceAtLeast(0)
                player.setMediaItems(items,index,offlineEvent?.positionMs ?: 0)
                player.prepare(); player.play()
                mutableState.value = AudioUiState(edition = edition, offline = true)
            } catch (error: Exception) { mutableState.value = mutableState.value.copy(error = error.message) }
            finally { opening = false }
        }
    }

    suspend fun stopOfflineAndSave(id: Long) {
        if (detail?.id != id || offlineEvent == null) return
        player.pause(); save(); offlineEvent = null; player.clearMediaItems()
        mutableState.value = AudioUiState()
    }

    fun toggle() { if (session != null || offlineEvent != null) { if (player.isPlaying) player.pause() else player.play() } }
    fun seek(position: Long) {
        if (session == null && offlineEvent == null) return
        player.seekTo(position.coerceIn(0, currentTrack()?.durationMs ?: 0))
        scope.launch { save() }
    }
    fun track(id: Long, position: Long = 0) {
        if (session == null && offlineEvent == null) return
        val index = detail?.tracks?.indexOfFirst { it.id == id } ?: return
        if (index < 0) return
        player.seekTo(index, position); player.play(); scope.launch { save() }
    }
    fun speed(value: Double) {
        scope.launch {
            try {
                val next = settings.copy(speed = value)
                api?.settings(next); settings = next; player.setPlaybackSpeed(value.toFloat()); publish()
            } catch (_: Exception) { mutableState.value = mutableState.value.copy(error = "倍速设置未保存") }
        }
    }
    fun sleep(minutes: Int?) { sleepAt = minutes?.let { SystemClock.elapsedRealtime() + it * 60000L }; publish() }
    fun bookmark() {
        val edition = detail ?: return
        val track = currentTrack() ?: return
        scope.launch {
            try { api?.bookmark(edition.id, AudioBookmarkDto(trackId = track.id, positionMs = player.currentPosition, title = "${track.title} ${player.currentPosition / 1000}秒")) }
            catch (_: Exception) { mutableState.value = mutableState.value.copy(error = "书签保存失败") }
        }
    }
    private fun currentTrack() = detail?.tracks?.find { it.id.toString() == player.currentMediaItem?.mediaId }
    private fun publish() {
        mutableState.value = mutableState.value.copy(edition = detail, track = currentTrack(),
            positionMs = player.currentPosition.coerceAtLeast(0), playing = player.isPlaying,
            speed = settings.speed, sleepRemainingMs = sleepAt?.minus(SystemClock.elapsedRealtime())?.coerceAtLeast(0))
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession
    override fun onTaskRemoved(rootIntent: Intent?) { if (!player.isPlaying) stopSelf() }
    override fun onDestroy() {
        player.pause(); player.release(); mediaSession?.release(); scope.cancel(); active = null
        mutableState.value = AudioUiState()
        super.onDestroy()
    }
}

data class AudioUiState(
    val edition: AudioEditionDto? = null,
    val track: AudioTrackDto? = null,
    val positionMs: Long = 0,
    val playing: Boolean = false,
    val speed: Double = 1.0,
    val sleepRemainingMs: Long? = null,
    val error: String? = null,
    val conflictId: Long? = null,
    val offline: Boolean = false
)
