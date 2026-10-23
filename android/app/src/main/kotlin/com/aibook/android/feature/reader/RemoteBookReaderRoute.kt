package com.aibook.android.feature.reader

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.app.Application
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aibook.android.core.model.LocalBook
import com.aibook.android.core.network.api.dto.BookVersionDTO
import com.aibook.android.di.ServiceLocator
import com.aibook.android.ui.design.SlidingSegmentedControl
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RemoteBookReaderRoute(bookId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { ServiceLocator.get(context).serverRepository }
    val scope = rememberCoroutineScope()
    var versions by remember(bookId) { mutableStateOf(emptyList<BookVersionDTO>()) }
    var activeVersionId by rememberSaveable(bookId) { mutableStateOf<Long?>(null) }
    var draftVersionId by remember(bookId) { mutableStateOf<Long?>(null) }
    var source by remember(bookId) { mutableStateOf<RemoteReaderSource?>(null) }
    var pickerVisible by remember(bookId) { mutableStateOf(false) }
    var opened by rememberSaveable(bookId) { mutableStateOf(false) }
    var loading by remember(bookId) { mutableStateOf(true) }
    var saving by remember(bookId) { mutableStateOf(false) }
    var error by remember(bookId) { mutableStateOf<String?>(null) }
    var retry by remember(bookId) { mutableStateOf(0) }

    LaunchedEffect(bookId, retry) {
        loading = true
        error = null
        try {
            val loadedVersions = repository.getBookVersions(bookId).getOrThrow()
            val openedVersionId = activeVersionId.takeIf { opened }
            val missingOpenedVersion = openedVersionId != null && loadedVersions.none { it.id == openedVersionId }
            val selected = resolveReaderVersion(loadedVersions,
                openedVersionId.takeUnless { missingOpenedVersion })
            versions = loadedVersions
            activeVersionId = selected.id
            draftVersionId = selected.id
            source = withContext(Dispatchers.IO) {
                // A local download has no version identity: only reuse it for a single-version book.
                if (loadedVersions.size > 1) RemoteReaderSource.Cloud
                else resolveRemoteReaderSource(bookId, ServiceLocator.get(context).bookRepository.observeBooks().first())
            }
            pickerVisible = requiresReaderVersionSelection(loadedVersions, openedVersionId)
            opened = !pickerVisible
            if (missingOpenedVersion) error = "上次阅读版本已不可用，请重新选择"
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "版本列表读取失败，请重试"
        } finally {
            loading = false
        }
    }

    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        !opened && !pickerVisible -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(error ?: "书籍没有可阅读版本")
            TextButton(onClick = { retry++ }) { Text("重试") }
            TextButton(onClick = onBack) { Text("返回") }
        }
        opened -> when (val current = source) {
            is RemoteReaderSource.Downloaded -> BookReaderRoute(current.localBookId, onBack)
            else -> key(bookId, activeVersionId) {
                // Clear the old reader and all pending chapter jobs on a version change.
                val owner: ReaderVersionSession = viewModel(key = "remote-reader-session-$bookId")
                owner.activate(activeVersionId)
                val reader: ReaderViewModel = viewModel(
                    viewModelStoreOwner = owner,
                    factory = ReaderViewModel.Factory,
                    extras = MutableCreationExtras().apply {
                        set(APPLICATION_KEY, context.applicationContext as Application)
                    }
                )
                ReaderScreen(
                    bookId = bookId.toString(),
                    isRemote = true,
                    onBack = onBack,
                    viewModel = reader,
                    requestedVersionId = activeVersionId,
                    versionLabel = versions.firstOrNull { it.id == activeVersionId }?.displayName,
                    onChooseVersion = if (versions.size > 1) ({
                        draftVersionId = activeVersionId
                        error = null
                        pickerVisible = true
                    }) else null
                )
            }
        }
    }

    if (pickerVisible) {
        val selected = versions.firstOrNull { it.id == draftVersionId }
        AlertDialog(
            onDismissRequest = {
                if (!saving) {
                    if (opened) pickerVisible = false else onBack()
                }
            },
            title = { Text("选择阅读版本") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        SlidingSegmentedControl(
                            options = versions.map { "${it.displayName} · ${it.format.uppercase()}" },
                            selectedIndex = versions.indexOfFirst { it.id == draftVersionId }.coerceAtLeast(0),
                            onSelected = { if (!saving) draftVersionId = versions[it].id },
                            modifier = Modifier.width((versions.size * 210).dp)
                        )
                    }
                    selected?.let {
                        Text(listOfNotNull(
                            if (it.primaryVersion) "主版本" else null,
                            it.chapterCount?.let { count -> "$count 章" },
                            if (it.id == activeVersionId && opened) "当前阅读版本" else null
                        ).joinToString(" · "))
                    }
                    Text("切换后恢复所选版本自己的进度，不按其他版本的章节位置跳转。PDF 等不支持在线阅读的格式请先下载。")
                    error?.let { Text(it) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !saving && selected != null,
                    onClick = {
                        val target = selected ?: return@TextButton
                        if (saving) return@TextButton
                        saving = true
                        error = null
                        scope.launch {
                            try {
                                repository.selectBookVersion(bookId, target.id).getOrThrow()
                                activeVersionId = target.id
                                source = RemoteReaderSource.Cloud
                                opened = true
                                pickerVisible = false
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                error = failure.message ?: "版本选择保存失败，请重试"
                            } finally {
                                saving = false
                            }
                        }
                    }
                ) { Text(if (saving) "正在保存…" else "阅读此版本") }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = {
                    if (opened) pickerVisible = false else onBack()
                }) { Text(if (opened) "继续当前版本" else "返回") }
            }
        )
    }
}

internal sealed interface RemoteReaderSource {
    data class Downloaded(val localBookId: String) : RemoteReaderSource
    data object Cloud : RemoteReaderSource
}

internal fun resolveRemoteReaderSource(bookId: Long, books: List<LocalBook>): RemoteReaderSource {
    val downloaded = books.firstOrNull { book ->
        book.remoteBookId == bookId && File(book.uri).let {
            it.isFile && it.canRead() && it.length() > 0L
        }
    }
    return downloaded?.let { RemoteReaderSource.Downloaded(it.id) } ?: RemoteReaderSource.Cloud
}
