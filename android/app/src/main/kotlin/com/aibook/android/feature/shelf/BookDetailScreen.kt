package com.aibook.android.feature.shelf

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aibook.android.core.data.repository.BookFileStats
import com.aibook.android.core.data.repository.RelocateBookResult
import com.aibook.android.core.model.BookFormat
import com.aibook.android.core.model.LocalBook
import com.aibook.android.core.reader.EpubContentParser
import com.aibook.android.core.reader.TextChapterParser
import com.aibook.android.di.ServiceLocator
import com.aibook.android.ui.design.BookCover
import com.aibook.android.ui.design.BookDetailTopBar
import com.aibook.android.ui.design.DetailActionButton
import com.aibook.android.ui.design.DetailInfoCard
import com.aibook.android.ui.design.DetailInfoItem
import com.aibook.android.ui.design.DetailIntroduction
import com.aibook.android.ui.design.DetailPrimaryButton
import com.aibook.android.ui.design.DetailTag
import com.aibook.android.ui.design.DesignTokens
import com.aibook.android.ui.design.SectionHeader
import com.aibook.android.ui.design.SoftCard
import com.aibook.android.ui.design.SourceBadge
import com.aibook.android.ui.design.WarmProgress
import com.aibook.android.feature.importer.supportedBookMimeTypes
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BookDetailScreen(
    bookId: String,
    onReadClick: () -> Unit,
    onBack: () -> Unit,
    onRelatedBookClick: (String) -> Unit = {},
    onSourcesClick: () -> Unit = {},
    viewModel: ShelfViewModel = viewModel(factory = ShelfViewModel.Factory)
) {
    val context = LocalContext.current
    val repository = remember { ServiceLocator.get(context.applicationContext as Application).bookRepository }
    val allBooksFlow = remember { repository.observeBooks() }
    val allBooks by allBooksFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val resolvedBookId = resolveLocalPreviewBookId(
        requestedBookId = bookId,
        firstLocalBookId = allBooks.firstOrNull()?.id,
        debugBuild = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    )
    val bookFlow = remember(resolvedBookId) { repository.observeBook(resolvedBookId.orEmpty()) }
    val book by bookFlow.collectAsStateWithLifecycle(initialValue = null as LocalBook?)
    val currentBook = book
    val scope = rememberCoroutineScope()
    var showMore by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var isRelocating by remember { mutableStateOf(false) }

    val relocatePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && currentBook != null) scope.launch {
            isRelocating = true
            actionMessage = when (val result = repository.relocateMissingBookFile(currentBook.id, uri)) {
                is RelocateBookResult.Success -> "文件已重新定位，阅读进度和书籍信息已保留"
                is RelocateBookResult.Failure -> "重新定位失败：${result.message}"
            }
            isRelocating = false
        }
    }

    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && currentBook != null) scope.launch {
            actionMessage = repository.replaceBookCover(currentBook.id, uri)
                .fold(onSuccess = { "封面已更新" }, onFailure = { "封面更新失败：${it.message ?: "未知错误"}" })
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = DesignTokens.PagePadding),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Space16)
    ) {
        BookDetailTopBar(
            favorite = currentBook?.favorite == true,
            onBack = onBack,
            onShare = { currentBook?.let { actionMessage = shareBook(context, it) } },
            onFavorite = { currentBook?.let { viewModel.setFavorite(it.id, !it.favorite) } },
            onMore = { showMore = true }
        )
        DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
            DropdownMenuItem(
                text = { Text("可用版本 / 来源") },
                leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                onClick = { showMore = false; onSourcesClick() }
            )
            DropdownMenuItem(
                text = { Text("编辑元数据") },
                leadingIcon = { Icon(Icons.Default.Edit, null) },
                onClick = { showMore = false; showEdit = true }
            )
            if (currentBook != null && !File(currentBook.uri).isFile) {
                DropdownMenuItem(
                    text = { Text("重新定位文件") },
                    leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                    onClick = { showMore = false; relocatePicker.launch(supportedBookMimeTypes) }
                )
            }
            DropdownMenuItem(
                text = { Text("替换封面") },
                leadingIcon = { Icon(Icons.Default.Image, null) },
                onClick = { showMore = false; coverPicker.launch("image/*") }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("删除或移出") },
                leadingIcon = { Icon(Icons.Default.Delete, null) },
                onClick = { showMore = false; showDelete = true }
            )
        }

        if (currentBook == null) {
            Text("加载中…")
            return@Column
        }

        actionMessage?.let { message ->
            SoftCard(color = Color.White) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(message, modifier = Modifier.weight(1f), color = DesignTokens.SoftText)
                    Text("关闭", color = DesignTokens.Accent, modifier = Modifier.clickable { actionMessage = null })
                }
            }
        }

        val fileMissing = !File(currentBook.uri).isFile
        if (fileMissing) {
            SoftCard(color = Color(0xFFFFF0EE)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("书籍文件已丢失", color = Color(0xFFB44A35), fontWeight = FontWeight.Bold)
                    Text("请选择原书文件进行修复。书籍 ID、阅读进度、书签、批注和元数据都会保留。", color = DesignTokens.SoftText)
                    Text(
                        if (isRelocating) "正在校验并复制…" else "重新定位文件",
                        modifier = Modifier
                            .clickable(enabled = !isRelocating) { relocatePicker.launch(supportedBookMimeTypes) }
                            .padding(vertical = 6.dp),
                        color = DesignTokens.Accent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        val chapterTitles by produceState<List<String>>(emptyList(), currentBook.id, currentBook.description) {
            value = if (currentBook.description.isNullOrBlank()) withContext(Dispatchers.IO) { loadChapterTitles(currentBook) } else emptyList()
        }
        val fileStats by produceState<BookFileStats?>(null, currentBook.id, currentBook.uri) {
            value = repository.bookFileStats(currentBook)
        }

        val progressPercent = (currentBook.progress.percent * 100).toInt()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space16),
            verticalAlignment = Alignment.Top
        ) {
            BookCover(title = currentBook.title, width = 132.dp, height = 204.dp, imageUri = currentBook.coverUri)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DesignTokens.Space12)) {
                Text(currentBook.title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(currentBook.author ?: "未知作者", style = MaterialTheme.typography.titleMedium, color = DesignTokens.TextPrimary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space8)) {
                    items(currentBook.tags.take(3).ifEmpty { listOf(currentBook.format.displayName) }) { DetailTag(it) }
                }
                Text(
                    listOfNotNull(
                        "$progressPercent%",
                        currentBook.progress.chapterTitle ?: currentBook.progress.positionLabel,
                        readingDurationLabel(currentBook.readingDurationSeconds).takeIf { currentBook.readingDurationSeconds > 0 }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge
                )
                WarmProgress(currentBook.progress.percent, Modifier.fillMaxWidth())
                DetailPrimaryButton(
                    label = if (progressPercent > 0) "继续阅读 · $progressPercent%" else "开始阅读",
                    enabled = !fileMissing,
                    onClick = onReadClick
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space12)) {
            DetailActionButton(
                icon = if (currentBook.shelved) Icons.Default.Check else Icons.AutoMirrored.Filled.MenuBook,
                label = if (currentBook.shelved) "已在书架" else "加入书架",
                selected = currentBook.shelved,
                onClick = { viewModel.toggleShelved(currentBook.id, !currentBook.shelved) }
            )
            DetailActionButton(
                icon = if (currentBook.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                label = if (currentBook.favorite) "已收藏" else "收藏",
                selected = currentBook.favorite,
                onClick = { viewModel.setFavorite(currentBook.id, !currentBook.favorite) }
            )
        }

        val description = when {
            !currentBook.description.isNullOrBlank() -> currentBook.description.orEmpty()
            chapterTitles.isNotEmpty() -> chapterTitles.take(5).joinToString("\n")
            else -> "暂无简介，可通过右上角菜单编辑书籍信息。"
        }
        DetailIntroduction(description)

        DetailInfoCard(
            items = listOf(
                DetailInfoItem("作者", currentBook.author ?: "未知作者", Icons.Default.Person),
                DetailInfoItem("格式", currentBook.format.displayName, Icons.Default.Description),
                DetailInfoItem("大小", fileSizeLabel(fileStats?.fileSizeBytes), Icons.Default.Storage),
                DetailInfoItem("文件路径", currentBook.uri, Icons.Default.FolderOpen),
                DetailInfoItem("来源", "本地文件", Icons.AutoMirrored.Filled.MenuBook)
            )
        )

        val versions = relatedVersions(currentBook, allBooks)
        val related = relatedBooks(currentBook, allBooks, versions.map { it.id }.toSet())
        if (versions.isNotEmpty()) BookRelationSection("关联版本", versions, onRelatedBookClick)
        if (related.isNotEmpty()) BookRelationSection("相关书籍", related, onRelatedBookClick)

        Spacer(Modifier.height(DesignTokens.Space24))
    }

    if (currentBook != null && showEdit) {
        MetadataEditDialog(
            book = currentBook,
            onDismiss = { showEdit = false },
            onSave = { title, author, description, rating, tags ->
                scope.launch {
                    repository.updateBookMetadata(currentBook.id, title, author, description, rating, tags)
                    actionMessage = "书籍信息已保存"
                    showEdit = false
                }
            }
        )
    }

    if (currentBook != null && showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除《${currentBook.title}》？", fontWeight = FontWeight.Bold) },
            text = { Text("“移出书库”会保留本地文件；“永久删除”会同时删除书籍记录、阅读数据和本地文件。") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    scope.launch { repository.deleteBook(currentBook.id); onBack() }
                }) { Text("永久删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showDelete = false }) { Text("取消") }
                    TextButton(onClick = {
                        showDelete = false
                        scope.launch { repository.removeFromStore(currentBook.id); onBack() }
                    }) { Text("移出书库") }
                }
            }
        )
    }
}

internal const val DEBUG_FIRST_LOCAL_BOOK_ID = "preview-first-local"

internal fun resolveLocalPreviewBookId(
    requestedBookId: String,
    firstLocalBookId: String?,
    debugBuild: Boolean
): String? = if (debugBuild && requestedBookId == DEBUG_FIRST_LOCAL_BOOK_ID) {
    firstLocalBookId
} else {
    requestedBookId
}

@Composable
private fun BookIdentity(book: LocalBook) {
    Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space16), verticalAlignment = Alignment.CenterVertically) {
        BookCover(title = book.title, width = 128.dp, height = 192.dp, imageUri = book.coverUri)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(book.title, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(book.author ?: "未知作者", color = DesignTokens.SoftText)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SourceBadge("本地")
                SourceBadge(book.format.displayName)
            }
            Text(
                book.rating?.let { "★ ${String.format(Locale.getDefault(), "%.1f", it)} / 10" } ?: "尚未评分",
                color = if (book.rating == null) DesignTokens.SoftText else DesignTokens.Accent,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun MetadataEditDialog(
    book: LocalBook,
    onDismiss: () -> Unit,
    onSave: (String, String?, String?, Float?, List<String>) -> Unit
) {
    var title by remember(book.id) { mutableStateOf(book.title) }
    var author by remember(book.id) { mutableStateOf(book.author.orEmpty()) }
    var description by remember(book.id) { mutableStateOf(book.description.orEmpty()) }
    var tags by remember(book.id) { mutableStateOf(book.tags.joinToString("，")) }
    var hasRating by remember(book.id) { mutableStateOf(book.rating != null) }
    var rating by remember(book.id) { mutableStateOf(book.rating ?: 5f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑书籍信息", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("书名") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(author, { author = it }, label = { Text("作者") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("简介") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(tags, { tags = it }, label = { Text("标签") }, supportingText = { Text("用逗号分隔") }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (hasRating) "个人评分 ${String.format(Locale.getDefault(), "%.1f", rating)}" else "尚未评分")
                    TextButton(onClick = { hasRating = !hasRating }) { Text(if (hasRating) "清除评分" else "添加评分") }
                }
                if (hasRating) Slider(value = rating, onValueChange = { rating = it }, valueRange = 0f..10f, steps = 19)
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    val parsedTags = tags.split(',', '，', ';', '；').map(String::trim).filter(String::isNotBlank)
                    onSave(title, author.ifBlank { null }, description.ifBlank { null }, rating.takeIf { hasRating }, parsedTags)
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun BookRelationSection(title: String, books: List<LocalBook>, onBookClick: (String) -> Unit) {
    SoftCard {
        SectionHeader(title, "${books.size} 本")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(books, key = { it.id }) { book ->
                Column(
                    modifier = Modifier.size(width = 92.dp, height = 150.dp).clickable { onBookClick(book.id) },
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BookCover(book.title, width = 72.dp, height = 102.dp, imageUri = book.coverUri)
                    Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                    Text(book.format.displayName, color = DesignTokens.SoftText, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun relatedVersions(book: LocalBook, allBooks: List<LocalBook>): List<LocalBook> {
    val title = book.title.trim().lowercase()
    return allBooks.filter { it.id != book.id && it.title.trim().lowercase() == title }.take(8)
}

private fun relatedBooks(book: LocalBook, allBooks: List<LocalBook>, versionIds: Set<String>): List<LocalBook> {
    val author = book.author?.trim()?.lowercase()
    val tags = book.tags.map(String::lowercase).toSet()
    return allBooks.asSequence()
        .filter { it.id != book.id && it.id !in versionIds }
        .map { candidate ->
            val sameAuthor = !author.isNullOrBlank() && candidate.author?.trim()?.lowercase() == author
            val sharedTags = candidate.tags.map(String::lowercase).count { it in tags }
            candidate to (sharedTags * 2 + if (sameAuthor) 1 else 0)
        }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .map { it.first }
        .take(8)
        .toList()
}

private fun shareBook(context: android.content.Context, book: LocalBook): String? {
    val file = File(book.uri)
    if (!file.isFile) return "文件不存在，无法分享"
    return runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType(book.format)
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, book.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享《${book.title}》"))
        null
    }.getOrElse { "分享失败：${it.message ?: "未知错误"}" }
}

private fun mimeType(format: BookFormat): String = when (format) {
    BookFormat.EPUB -> "application/epub+zip"
    BookFormat.PDF -> "application/pdf"
    BookFormat.TXT -> "text/plain"
    BookFormat.HTML, BookFormat.HTM -> "text/html"
    BookFormat.MARKDOWN -> "text/markdown"
    BookFormat.MOBI, BookFormat.AZW3 -> "application/octet-stream"
}

private fun fileSizeLabel(bytes: Long?): String {
    if (bytes == null) return "计算中"
    if (bytes <= 0) return "文件缺失"
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1) String.format(Locale.getDefault(), "%.1f MB", mb) else "${bytes / 1024} KB"
}

private fun wordCountLabel(value: Long?): String = value?.let {
    if (it >= 10_000) String.format(Locale.getDefault(), "%.1f 万", it / 10_000.0) else "$it 字"
} ?: "暂不可用"

private fun readingDurationLabel(seconds: Long): String {
    val minutes = seconds.coerceAtLeast(0) / 60
    return if (minutes >= 60) "${minutes / 60}小时${minutes % 60}分" else "${minutes}分"
}

private fun lastReadLabel(lastReadAt: Instant?): String {
    if (lastReadAt == null) return "尚未开始"
    val minutes = Duration.between(lastReadAt, Instant.now()).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "$minutes 分钟前"
        minutes < 24 * 60 -> "${minutes / 60} 小时前"
        else -> "${minutes / (24 * 60)} 天前"
    }
}

@Composable
private fun InfoItem(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = DesignTokens.SoftText, style = MaterialTheme.typography.bodySmall)
        Text(value, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun DetailInfoRow(label: String, value: String, showDivider: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = DesignTokens.Space12),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = DesignTokens.SoftText)
        Text(value, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (showDivider) HorizontalDivider(color = DesignTokens.Hairline)
}

private fun loadChapterTitles(book: LocalBook): List<String> = runCatching {
    val file = File(book.uri)
    if (!file.exists()) return emptyList()
    when (book.format) {
        BookFormat.EPUB -> EpubContentParser.parse(file.readBytes()).chapters.map { it.title }
        BookFormat.TXT, BookFormat.MARKDOWN, BookFormat.HTML, BookFormat.HTM -> TextChapterParser.parse(file.readText()).map { it.title }
        else -> emptyList()
    }
}.getOrDefault(emptyList())
