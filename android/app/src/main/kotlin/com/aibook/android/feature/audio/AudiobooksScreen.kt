package com.aibook.android.feature.audio

import android.content.ComponentName
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aibook.android.core.network.api.AudioEditionDto
import com.aibook.android.di.ServiceLocator
import com.aibook.android.ui.design.SlidingSegmentedControl
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import com.aibook.android.core.network.api.AudioOfflineProgressDto
import com.aibook.android.core.network.api.AudioProgressDto

@Composable
fun AudiobooksScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locator = remember { ServiceLocator.get(context) }
    val repository = locator.serverRepository
    val account by locator.serverConfigStore.username.collectAsState(initial = null)
    val server by locator.serverConfigStore.serverUrl.collectAsState(initial = "")
    val playback by AudioPlaybackService.state.collectAsState()
    var entries by remember { mutableStateOf<List<AudioEditionDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(0) }
    var total by remember { mutableLongStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var slider by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val offline = remember(account,server) { AudioOfflineStore(context.applicationContext) }
    var cached by remember { mutableStateOf<List<AudioEditionDto>>(emptyList()) }
    var download by remember { mutableStateOf<Job?>(null) }
    var downloadMessage by remember { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<Long?>(null) }
    var conflict by remember { mutableStateOf<Triple<AudioEditionDto,AudioOfflineProgressDto,AudioProgressDto>?>(null) }
    suspend fun refreshOffline() { cached = offline.list() }
    suspend fun synchronize(edition: AudioEditionDto, resolve: Boolean = false) {
        AudioPlaybackService.active?.stopOfflineAndSave(edition.id)
        val pending = offline.progress(edition.id) ?: return
        val api = repository.audioApi()
        val server = api.detail(edition.id)
        if (server.contentRevision != pending.contentRevision) {
            error = "音轨已变化，离线位置保留；请重新下载核对"
            return
        }
        try {
            val result = api.offlineProgress(edition.id, if (resolve) pending.copy(baseRevision = server.progress.revision, resolveConflict = true) else pending)
            offline.clearProgress(edition.id)
            offline.updateManifest(edition.copy(progress = result))
            conflict = null; error = null; refreshOffline()
        } catch (failure: retrofit2.HttpException) {
            if (failure.code() == 409) conflict = Triple(edition,pending,server.progress) else throw failure
        }
    }
    LaunchedEffect(account,server) {
        cached = emptyList(); entries = emptyList()
        runCatching { refreshOffline() }.onFailure { error = it.message }
    }
    var speedMenu by remember { mutableStateOf(false) }
    var sleepMenu by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val future = MediaController.Builder(context,
            SessionToken(context, ComponentName(context, AudioPlaybackService::class.java))).buildAsync()
        future.addListener({ ready = runCatching { future.get(); true }.getOrDefault(false) }, ContextCompat.getMainExecutor(context))
        onDispose { MediaController.releaseFuture(future) }
    }
    LaunchedEffect(page,account,server) {
        runCatching { repository.audioApi().list(page) }
            .onSuccess { entries = it.content; total = it.totalElements; error = null }
            .onFailure { error = "无法读取有声书：${it.message}" }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("返回") }
            Text("有声书", style = MaterialTheme.typography.titleLarge)
            if (playback.edition != null) TextButton(onClick = { tab = 1 }) { Text("正在收听") }
        }
        SlidingSegmentedControl(options = listOf("书库", "播放", "章节", "离线"), selectedIndex = tab, onSelected = { tab = it })
        downloadMessage?.let { Text(it) }
        if (download?.isActive == true) TextButton(onClick = { download?.cancel(); downloadMessage = "下载已暂停，可再次下载以续传" }) { Text("取消下载") }
        deleteId?.let { id ->
            AlertDialog(onDismissRequest = { deleteId = null }, title = { Text("丢弃离线进度并清理？") },
                text = { Text("未同步的本机收听位置将被丢弃；服务器位置和原始音频不受影响。") },
                confirmButton = { TextButton(onClick = { scope.launch {
                    AudioPlaybackService.active?.stopOfflineAndSave(id)
                    offline.clearProgress(id); offline.remove(id); deleteId = null; refreshOffline()
                } }) { Text("确认丢弃并清理") } },
                dismissButton = { TextButton(onClick = { deleteId = null }) { Text("取消") } })
        }
        conflict?.let { (edition, local, server) ->
            AlertDialog(onDismissRequest = { conflict = null }, title = { Text("离线进度冲突") },
                text = { Text("此设备：音轨 ${local.trackId} ${audioTime(local.positionMs)}\n另一设备：音轨 ${server.trackId ?: 0} ${audioTime(server.positionMs)}\n选择前保留两份位置。") },
                confirmButton = { TextButton(onClick = { scope.launch { runCatching { synchronize(edition,true) }.onFailure { error = it.message } } }) { Text("使用此设备进度") } },
                dismissButton = { TextButton(onClick = { scope.launch {
                    offline.clearProgress(edition.id)
                    offline.updateManifest(edition.copy(progress = server))
                    conflict = null; refreshOffline()
                } }) { Text("使用服务器进度") } })
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        playback.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        playback.conflictId?.let { id ->
            Row {
                TextButton(onClick = { AudioPlaybackService.active?.open(id, true) }) { Text("接管并使用服务器进度") }
            }
        }
        when (tab) {
            0 -> {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(entries, key = { it.id }) { edition ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(edition.title, style = MaterialTheme.typography.titleMedium)
                                Text("${edition.name} · ${edition.narrator}")
                                TextButton(enabled = ready, onClick = { AudioPlaybackService.active?.open(edition.id); tab = 1 }) { Text("开始 / 继续收听") }
                                TextButton(enabled = download?.isActive != true, onClick = {
                                    download = scope.launch {
                                        runCatching { offline.download(edition.id) { downloadMessage = it } }
                                            .onSuccess { downloadMessage = "下载完成"; refreshOffline() }
                                            .onFailure { error = it.message }
                                    }
                                }) { Text("下载离线音频") }
                            }
                        }
                    }
                    if (entries.isEmpty()) item { Text("暂无有声书，请先从 Web 上传或扫描导入。") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(enabled = page > 0, onClick = { page-- }) { Text("上一页") }
                    Text("${page + 1}")
                    TextButton(enabled = (page + 1) * 24 < total, onClick = { page++ }) { Text("下一页") }
                }
            }
            1 -> {
                val edition = playback.edition
                if (edition == null) Text("选择一本有声书开始收听。")
                else {
                    Spacer(Modifier.height(32.dp))
                    Text(edition.title, style = MaterialTheme.typography.headlineMedium)
                    Text("演播：${edition.narrator}")
                    Text(playback.track?.title.orEmpty(), style = MaterialTheme.typography.titleMedium)
                    Slider(value = if (dragging) slider else playback.positionMs.toFloat(),
                        onValueChange = { dragging = true; slider = it },
                        valueRange = 0f..(playback.track?.durationMs ?: 1).coerceAtLeast(1).toFloat(),
                        onValueChangeFinished = { AudioPlaybackService.active?.seek(slider.toLong()); dragging = false })
                    Text("${audioTime(playback.positionMs)} / ${audioTime(playback.track?.durationMs ?: 0)}")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TextButton(onClick = { AudioPlaybackService.active?.seek(playback.positionMs - 15000) }) { Text("后退15秒") }
                        TextButton(onClick = { AudioPlaybackService.active?.toggle() }) { Text(if (playback.playing) "暂停" else "播放") }
                        TextButton(onClick = { AudioPlaybackService.active?.seek(playback.positionMs + 30000) }) { Text("前进30秒") }
                    }
                    Row {
                        Box {
                            TextButton(onClick = { speedMenu = true }) { Text("${playback.speed}×") }
                            DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                                listOf(0.75,1.0,1.25,1.5,2.0).forEach { speed -> DropdownMenuItem(text = { Text("$speed×") }, onClick = { AudioPlaybackService.active?.speed(speed); speedMenu = false }) }
                            }
                        }
                        Box {
                            TextButton(onClick = { sleepMenu = true }) { Text(playback.sleepRemainingMs?.let { "${it / 60000 + 1}分钟后停止" } ?: "睡眠定时") }
                            DropdownMenu(expanded = sleepMenu, onDismissRequest = { sleepMenu = false }) {
                                listOf(15,30,60).forEach { minutes -> DropdownMenuItem(text = { Text("$minutes 分钟") }, onClick = { AudioPlaybackService.active?.sleep(minutes); sleepMenu = false }) }
                                DropdownMenuItem(text = { Text("关闭定时") }, onClick = { AudioPlaybackService.active?.sleep(null); sleepMenu = false })
                            }
                        }
                        TextButton(onClick = { AudioPlaybackService.active?.bookmark() }) { Text("添加书签") }
                    }
                    Text("支持系统锁屏与耳机控制；实际行为需对应设备验证。", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { AudioPlaybackService.active?.open(edition.id) }) { Text("重新打开收听") }
                }
            }
            3 -> LazyColumn(Modifier.weight(1f)) {
                items(cached, key = { it.id }) { edition ->
                    Column(Modifier.padding(12.dp)) {
                        Text(edition.title,style = MaterialTheme.typography.titleMedium)
                        TextButton(enabled = ready,onClick = { AudioPlaybackService.active?.openOffline(edition); tab = 1 }) { Text("离线收听") }
                        TextButton(onClick = { scope.launch { runCatching { synchronize(edition) }.onFailure { error = it.message } } }) { Text("同步离线进度") }
                        TextButton(onClick = { scope.launch {
                            AudioPlaybackService.active?.stopOfflineAndSave(edition.id)
                            if (offline.progress(edition.id) != null) deleteId = edition.id
                            else { offline.remove(edition.id); refreshOffline() }
                        } }) { Text("清理离线文件") }
                    }
                }
                if (cached.isEmpty()) item { Text("尚无完整下载的有声书。缓存限额 5GB；下载失败可重试续传。") }
            }
            2 -> LazyColumn(Modifier.weight(1f)) {
                items(playback.edition?.chapters.orEmpty(), key = { it.id }) { chapter ->
                    TextButton(onClick = { chapter.segments.firstOrNull()?.let { AudioPlaybackService.active?.track(it.trackId,it.startMs) } }) { Text(chapter.title) }
                }
            }
        }
    }
}

private fun audioTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
