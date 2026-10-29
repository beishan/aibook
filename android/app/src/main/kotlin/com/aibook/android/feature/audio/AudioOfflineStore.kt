package com.aibook.android.feature.audio

import android.content.Context
import com.aibook.android.core.network.api.*
import com.aibook.android.di.ServiceLocator
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*

/** App-private audio files are isolated by existing server/account namespace. */
class AudioOfflineStore(private val context: Context) {
    private val repository get() = ServiceLocator.get(context).serverRepository
    private var namespace: String? = null
    private suspend fun root(): File {
        val current = repository.structuredCacheNamespace()
        check(namespace == null || namespace == current) { "账户已切换" }
        namespace = current
        return File(context.noBackupFilesDir, "audio/$current").also { it.mkdirs() }
    }
    private suspend fun folder(id: Long) = File(root(), id.toString()).also { it.mkdirs() }

    suspend fun list(): List<AudioEditionDto> = withContext(Dispatchers.IO) {
        root().listFiles().orEmpty().filter(File::isDirectory).mapNotNull { file ->
            runCatching { AudioJson.decode(File(file,"manifest.json").readText()) }.getOrNull()
        }
    }
    suspend fun file(editionId: Long, trackId: Long): File = File(folder(editionId), "$trackId.audio")
    suspend fun progress(id: Long): AudioOfflineProgressDto? = withContext(Dispatchers.IO) {
        runCatching { AudioJson.decodeProgress(File(folder(id),"progress.json").readText()) }.getOrNull()
    }
    suspend fun save(id: Long, value: AudioOfflineProgressDto) = withContext(Dispatchers.IO) {
        atomic(File(folder(id),"progress.json"),AudioJson.encodeProgress(value))
    }
    suspend fun updateManifest(manifest: AudioEditionDto) = withContext(Dispatchers.IO) {
        atomic(File(folder(manifest.id),"manifest.json"),AudioJson.encode(manifest))
    }
    suspend fun clearProgress(id: Long) = withContext(Dispatchers.IO) { File(folder(id),"progress.json").delete(); Unit }
    suspend fun remove(id: Long) = withContext(Dispatchers.IO) { folder(id).deleteRecursively(); Unit }

    suspend fun download(id: Long, notify: (String) -> Unit) = withContext(Dispatchers.IO) {
        val namespace = repository.structuredCacheNamespace()
        val api = repository.audioApi()
        val manifest = api.manifest(id)
        val directory = folder(id)
        val required = manifest.tracks.sumOf { it.fileSize }
        val remaining = manifest.tracks.sumOf { track ->
            val completed = File(directory,"${track.id}.audio")
            val partial = File(directory,"${track.id}.part")
            if (completed.exists() && completed.length() == track.fileSize && digest(completed) == track.fileHash) 0L
            else (track.fileSize - partial.length()).coerceAtLeast(0)
        }
        val quota = 5L * 1024 * 1024 * 1024
        val used = root().walkTopDown().filter(File::isFile).sumOf { it.length() }
        require(required in 1..quota && used + remaining <= quota) { "离线音频缓存限额 5GB，请先清理已下载内容" }
        require(root().usableSpace > remaining + 100L * 1024 * 1024) { "设备空间不足" }
        manifest.tracks.forEachIndexed { index, track ->
            currentCoroutineContext().ensureActive()
            require(namespace == repository.structuredCacheNamespace()) { "账户已切换，下载停止" }
            val final = File(directory,"${track.id}.audio")
            if (final.exists() && final.length() == track.fileSize && digest(final) == track.fileHash) return@forEachIndexed
            val partial = File(final.parentFile, "${track.id}.part")
            val offset = partial.takeIf { it.exists() }?.length() ?: 0
            if (offset == track.fileSize && digest(partial) == track.fileHash) {
                check(partial.renameTo(final)); return@forEachIndexed
            }
            if (offset > track.fileSize) { partial.delete(); throw IllegalArgumentException("临时音轨异常，请重试") }
            val response = api.download(id, track.id, if (offset > 0) "bytes=$offset-" else null)
            require(response.isSuccessful) { "音轨下载失败：${response.code()}" }
            val append = response.code() == 206 && offset > 0
            if (append) require(response.headers()["Content-Range"]?.startsWith("bytes $offset-") == true) { "断点响应不匹配" }
            response.body()!!.use { body ->
                body.byteStream().use { input ->
                    java.io.FileOutputStream(partial, append).use { output ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer,0,count)
                            require(partial.length() <= track.fileSize) { "音频文件大小超出清单" }
                        }
                    }
                }
            }
            if (partial.length() != track.fileSize || digest(partial) != track.fileHash) {
                partial.delete()
                throw IllegalArgumentException("音轨校验失败，请重新下载")
            }
            require(partial.renameTo(final)) { "无法保存音轨" }
            withContext(Dispatchers.Main) { notify("已下载 ${index + 1}/${manifest.tracks.size} 集") }
        }
        require(namespace == repository.structuredCacheNamespace()) { "账户已切换" }
        atomic(File(directory,"manifest.json"),AudioJson.encode(manifest))
    }
    private fun atomic(file: File, value: String) {
        val pending = File(file.parentFile,file.name + ".tmp")
        pending.writeText(value)
        check(pending.renameTo(file)) { "无法保存离线状态" }
    }
    private fun digest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer,0,count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
