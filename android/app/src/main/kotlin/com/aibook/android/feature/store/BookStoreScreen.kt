package com.aibook.android.feature.store

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Duration
import java.time.Instant
import kotlin.math.absoluteValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aibook.android.ui.design.BookCover
import com.aibook.android.ui.design.BookDetailTopBar
import com.aibook.android.ui.design.CoverSourceBadge
import com.aibook.android.ui.design.DetailActionButton
import com.aibook.android.ui.design.DetailInfoCard
import com.aibook.android.ui.design.DetailInfoItem
import com.aibook.android.ui.design.DetailIntroduction
import com.aibook.android.ui.design.DetailPrimaryButton
import com.aibook.android.ui.design.DetailTag
import com.aibook.android.ui.design.DesignPage
import com.aibook.android.ui.design.DesignTokens
import com.aibook.android.ui.design.SoftCard
import com.aibook.android.ui.design.SourceBadge
import com.aibook.android.ui.design.SlidingSegmentedControl
import com.aibook.android.ui.design.SectionHeader
import com.aibook.android.core.data.repository.DownloadStatus

@Composable
fun BookStoreScreen(
    onServerLibraryClick: () -> Unit = {},
    onCategoryClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onDownloadsClick: () -> Unit = {},
    onBookClick: (String) -> Unit = {},
    onRemoteBookClick: (String) -> Unit = {},
    initialSourceIndex: Int = 0,
    viewModel: StoreViewModel = viewModel(factory = StoreViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    val actionState by viewModel.actionState.collectAsState()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("store_prefs", Context.MODE_PRIVATE) }
    // 新视觉稿仅保留卡片与列表两种模式，并兼容清理旧版本保存的 2/3 模式。
    var viewMode by remember { mutableIntStateOf(prefs.getInt("view_mode", 0).coerceIn(0, 1)) }
    var collectionFilter by remember { mutableIntStateOf(0) }
    var managementMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var shelfRemovalBook by remember { mutableStateOf<StoreBook?>(null) }
    var sourceIndex by remember { mutableIntStateOf(initialSourceIndex.coerceIn(0, 1)) }
    val sourceBooks = uiState.filteredBooks.filter {
        if (sourceIndex == 0) it.kind == StoreItemKind.LOCAL else it.kind == StoreItemKind.OPDS
    }
    val filteredBooks = sourceBooks.filter { book ->
        when (collectionFilter) {
            1 -> if (sourceIndex == 0) book.shelved else book.isDownloaded
            2 -> if (sourceIndex == 0) !book.shelved else !book.isDownloaded
            else -> true
        }
    }
    val localBooks = filteredBooks.filter { it.kind == StoreItemKind.LOCAL }
    val selectedLocalBooks = filteredBooks.filter { it.kind == StoreItemKind.LOCAL && it.id in selectedIds }
    val allLocalSelected = localBooks.isNotEmpty() && localBooks.all { it.id in selectedIds }
    val openBook: (StoreBook) -> Unit = { book ->
        when {
            book.kind == StoreItemKind.LOCAL -> onBookClick(book.id)
            book.downloadedLocalId != null -> onBookClick(book.downloadedLocalId)
            book.kind == StoreItemKind.OPDS -> onRemoteBookClick(book.id)
        }
    }

    DesignPage(
        title = if (managementMode) {
            "已选 ${selectedLocalBooks.size} 本"
        } else if (sourceIndex == 0) {
            "本地书籍"
        } else {
            "OPDS 书籍"
        },
        modifier = Modifier.fillMaxSize(),
        actions = {
            Icon(
                Icons.Default.Download,
                contentDescription = "下载管理",
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDownloadsClick
                )
            )
            Icon(
                Icons.Default.Search,
                contentDescription = "全局搜索",
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSearchClick
                )
            )
            Icon(
                Icons.Default.FilterList,
                contentDescription = "筛选",
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCategoryClick
                )
            )
            if (sourceIndex == 0) {
                Text(
                    if (managementMode) "取消" else "管理",
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        managementMode = !managementMode
                        selectedIds = emptySet()
                    }
                )
            }
        }
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Space16)
        ) {
            SlidingSegmentedControl(
                options = listOf("本地书籍", "OPDS", "后端服务"),
                selectedIndex = sourceIndex,
                onSelected = {
                    when (it) {
                        0, 1 -> {
                            sourceIndex = it
                            collectionFilter = 0
                            managementMode = false
                            selectedIds = emptySet()
                        }
                        2 -> onServerLibraryClick()
                    }
                }
            )
            SlidingSegmentedControl(
                options = if (sourceIndex == 0) {
                    listOf(
                        "全部 ${sourceBooks.size}",
                        "已加入 ${sourceBooks.count { it.shelved }}",
                        "未加入 ${sourceBooks.count { !it.shelved }}"
                    )
                } else {
                    listOf(
                        "全部 ${sourceBooks.size}",
                        "已下载 ${sourceBooks.count { it.isDownloaded }}",
                        "未下载 ${sourceBooks.count { !it.isDownloaded }}"
                    )
                },
                selectedIndex = collectionFilter,
                onSelected = { collectionFilter = it }
            )
            actionState.message?.let { message ->
                SoftCard(color = Color.White) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(message, color = DesignTokens.SoftText, modifier = Modifier.weight(1f))
                        Text(
                            "关闭",
                            color = DesignTokens.Accent,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { viewModel.clearMessage() }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            if (managementMode) {
                StoreManagementBar(
                    selectedCount = selectedLocalBooks.size,
                    allLocalSelected = allLocalSelected,
                    hasLocalBooks = localBooks.isNotEmpty(),
                    onSelectAll = {
                        selectedIds = if (allLocalSelected) {
                            selectedIds - localBooks.map { it.id }.toSet()
                        } else {
                            selectedIds + localBooks.map { it.id }
                        }
                    },
                    onRemove = {
                        viewModel.removeLocalBooksFromStore(selectedLocalBooks)
                        selectedIds = emptySet()
                        managementMode = false
                    }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        if (sourceIndex == 0) "我的本地书库" else "可下载书籍",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text("共 ${filteredBooks.size} 本", color = DesignTokens.SoftText)
                }
                SlidingSegmentedControl(
                    options = listOf("卡片", "列表"),
                    selectedIndex = viewMode,
                    onSelected = { mode ->
                        viewMode = mode
                        prefs.edit().putInt("view_mode", mode).apply()
                    },
                    modifier = Modifier.width(156.dp)
                )
            }
            when (viewMode) {
                0 -> {
                    // 网格视图
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) {
                        gridItems(filteredBooks) { book ->
                            StoreBookCard(
                                book = book,
                                downloading = actionState.downloadTasks[book.id]?.status in setOf(DownloadStatus.QUEUED, DownloadStatus.RUNNING),
                                managementMode = managementMode,
                                selected = book.id in selectedIds,
                                onBookClick = {
                                    if (managementMode && it.kind == StoreItemKind.LOCAL) {
                                        selectedIds = toggleSelection(selectedIds, it.id)
                                    } else {
                                        openBook(it)
                                    }
                                },
                                onDownloadClick = viewModel::downloadRemoteBook,
                                onLocalShelfClick = viewModel::addLocalBookToShelf,
                                onLocalShelfRemoveClick = { shelfRemovalBook = it }
                            )
                        }
                    }
                }
                1 -> {
                    // 带封面列表视图
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) {
                        items(filteredBooks) { book ->
                            StoreListItem(
                                book = book,
                                downloading = actionState.downloadTasks[book.id]?.status in setOf(DownloadStatus.QUEUED, DownloadStatus.RUNNING),
                                managementMode = managementMode,
                                selected = book.id in selectedIds,
                                onBookClick = {
                                    if (managementMode && it.kind == StoreItemKind.LOCAL) {
                                        selectedIds = toggleSelection(selectedIds, it.id)
                                    } else {
                                        openBook(it)
                                    }
                                },
                                onDownloadClick = viewModel::downloadRemoteBook,
                                onLocalShelfClick = viewModel::addLocalBookToShelf,
                                onLocalShelfRemoveClick = { shelfRemovalBook = it }
                            )
                        }
                    }
                }
            }
//            SectionHeader("最近更新", "更多更新 ›")
//            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
//                items(recentBooks) { book ->
//                    RecentUpdateCard(book)
//                }
//            }
        }
    }

    shelfRemovalBook?.let { book ->
        AlertDialog(
            onDismissRequest = { shelfRemovalBook = null },
            title = { Text("移出书架", fontWeight = FontWeight.Bold) },
            text = { Text("确定要将《${book.title}》移出书架吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeLocalBookFromShelf(book)
                        shelfRemovalBook = null
                    }
                ) { Text("移出") }
            },
            dismissButton = {
                TextButton(onClick = { shelfRemovalBook = null }) { Text("取消") }
            }
        )
    }
}

private fun toggleSelection(selectedIds: Set<String>, id: String): Set<String> {
    return if (id in selectedIds) selectedIds - id else selectedIds + id
}

@Composable
fun StoreRemoteBookDetailScreen(
    bookId: String,
    onBack: () -> Unit,
    onOpenLocalBook: (String) -> Unit,
    viewModel: StoreViewModel = viewModel(factory = StoreViewModel.Factory)
) {
    val books by viewModel.books.collectAsState()
    val actionState by viewModel.actionState.collectAsState()
    val context = LocalContext.current
    var favorite by rememberSaveable(bookId) { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var selectedFormat by rememberSaveable(bookId) { mutableIntStateOf(0) }
    var detailMessage by remember { mutableStateOf<String?>(null) }
    val book = remember(books, bookId) {
        books.firstOrNull { it.id == bookId && it.kind == StoreItemKind.OPDS }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DesignTokens.AppBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DesignTokens.PagePadding),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Space16)
    ) {
        BookDetailTopBar(
            title = "书籍详情 · OPDS",
            favorite = favorite,
            onBack = onBack,
            onShare = {
                val current = book ?: return@BookDetailTopBar
                val text = listOf(current.title, current.author, current.acquisitionHref).filterNotNull().joinToString("\n")
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }, "分享书籍"))
            },
            onFavorite = { favorite = !favorite; detailMessage = if (favorite) "已收藏" else "已取消收藏" },
            onMore = { showMore = true }
        )
        DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
            DropdownMenuItem(
                text = { Text("来源：${book?.sourceName ?: "OPDS"}") },
                onClick = { showMore = false }
            )
            DropdownMenuItem(
                text = { Text("刷新书籍信息") },
                onClick = { showMore = false; detailMessage = "书籍信息来自最近一次 OPDS 同步" }
            )
        }

        if (book == null) {
            SoftCard(color = Color.White) {
                Text("书籍不存在或数据源已停用", color = DesignTokens.SoftText)
            }
            return@Column
        }

        (detailMessage ?: actionState.message)?.let { message ->
            SoftCard(color = Color.White) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(message, color = DesignTokens.SoftText, modifier = Modifier.weight(1f))
                    Text(
                        "关闭",
                        color = DesignTokens.Accent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { detailMessage = null; viewModel.clearMessage() }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space16), modifier = Modifier.fillMaxWidth()) {
            BookCover(
                title = book.title,
                width = 132.dp,
                height = 204.dp,
                imageUri = book.coverUri,
                brush = Brush.verticalGradient(listOf(titleColor(book.title), Color(0xFF1C1B18)))
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DesignTokens.Space12)) {
                Text(book.title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(book.author, style = MaterialTheme.typography.titleMedium)
                DetailTag(book.sourceName)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(book.categories.take(3).ifEmpty { listOf("未分类") }) { DetailTag(it) }
                }
                Text(book.format.uppercase(), color = DesignTokens.SoftText, style = MaterialTheme.typography.bodyLarge)
                DetailPrimaryButton(
                    label = if (book.downloadedLocalId != null) "开始阅读" else "下载后阅读",
                    onClick = {
                        book.downloadedLocalId?.let(onOpenLocalBook) ?: viewModel.downloadRemoteBook(book)
                    }
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space8)) {
            DetailActionButton(Icons.Default.Download, if (book.isDownloaded) "已下载" else "下载", book.isDownloaded) {
                book.downloadedLocalId?.let(onOpenLocalBook) ?: viewModel.downloadRemoteBook(book)
            }
            DetailActionButton(Icons.AutoMirrored.Filled.MenuBook, if (book.isDownloaded) "已在书架" else "加入书架", book.isDownloaded) {
                book.downloadedLocalId?.let(onOpenLocalBook) ?: viewModel.downloadRemoteBook(book)
            }
            DetailActionButton(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (favorite) "已收藏" else "收藏", favorite) {
                favorite = !favorite
                detailMessage = if (favorite) "已收藏" else "已取消收藏"
            }
        }

        val formats = book.format.split('/', ',', '，', ';').map(String::trim).filter(String::isNotBlank).ifEmpty { listOf("EPUB") }
        SoftCard(color = DesignTokens.CardBackground) {
            Text("可用格式", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            LazyRow(Modifier.padding(top = DesignTokens.Space12), horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space12)) {
                items(formats.indices.toList()) { index ->
                    val format = formats[index].uppercase()
                    Surface(
                        modifier = Modifier.width(112.dp).height(116.dp),
                        shape = RoundedCornerShape(DesignTokens.RadiusMedium),
                        color = DesignTokens.CardBackground,
                        border = BorderStroke(1.dp, if (selectedFormat == index) DesignTokens.Accent else DesignTokens.Hairline),
                        onClick = { selectedFormat = index }
                    ) {
                        Column(Modifier.padding(DesignTokens.Space12), verticalArrangement = Arrangement.spacedBy(DesignTokens.Space8)) {
                            Text(if (selectedFormat == index) "●" else "○", color = if (selectedFormat == index) DesignTokens.Accent else DesignTokens.SoftText)
                            Text(format, fontWeight = FontWeight.Bold)
                            Text(formatDescription(format), color = DesignTokens.SoftText, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        DetailIntroduction(book.summary?.takeIf(String::isNotBlank) ?: "暂无简介", card = true)

        DetailInfoCard(
            items = listOf(
                DetailInfoItem("来源", book.sourceName),
                DetailInfoItem("地址", book.acquisitionHref ?: "由 OPDS 服务提供"),
                DetailInfoItem("格式", formats.getOrElse(selectedFormat) { formats.first() }.uppercase()),
                DetailInfoItem("语言", "未知")
            )
        )

        val task = actionState.downloadTasks[book.id]
        val downloading = task?.status in setOf(DownloadStatus.QUEUED, DownloadStatus.RUNNING)
        if (task != null && task.status != DownloadStatus.COMPLETED) {
            SoftCard(color = Color.White) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("下载任务 · ${task.progress}%", fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(progress = { task.progress / 100f }, modifier = Modifier.fillMaxWidth())
                    task.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (task.status) {
                            DownloadStatus.RUNNING, DownloadStatus.QUEUED -> TextButton(onClick = { viewModel.pauseDownload(book.id) }) { Text("暂停") }
                            DownloadStatus.PAUSED -> TextButton(onClick = { viewModel.resumeDownload(book.id) }) { Text("继续") }
                            DownloadStatus.FAILED -> TextButton(onClick = { viewModel.retryDownload(book.id) }) { Text("重试") }
                            else -> Unit
                        }
                        if (task.status !in setOf(DownloadStatus.COMPLETED, DownloadStatus.CANCELLED)) {
                            TextButton(onClick = { viewModel.cancelDownload(book.id) }) { Text("取消") }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(DesignTokens.Space24))
    }
}

private fun formatDescription(format: String): String = when (format.uppercase()) {
    "EPUB" -> "电子书格式"
    "MOBI", "AZW3" -> "Kindle 格式"
    "PDF" -> "便携式文档"
    "TXT" -> "纯文本格式"
    else -> "可用电子书格式"
}

@Composable
private fun StoreSourceSegment(
    selected: String?,
    sources: List<Pair<String, String>>,
    onSelect: (String?) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8F4F0), RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        (listOf(null to "全部") + sources).forEach { (sourceId, label) ->
            Text(
                text = label,
                color = if (sourceId == selected) DesignTokens.Accent else Color.Black,
                fontWeight = if (sourceId == selected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (sourceId == selected) Color.White else Color.Transparent,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(sourceId) }
                    .padding(vertical = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun StoreChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Text(
        label,
        color = if (selected) DesignTokens.Accent else Color.Black,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .background(
                if (selected) DesignTokens.Accent.copy(alpha = 0.08f) else Color.Transparent,
                RoundedCornerShape(18.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun StoreManagementBar(
    selectedCount: Int,
    allLocalSelected: Boolean,
    hasLocalBooks: Boolean,
    onSelectAll: () -> Unit,
    onRemove: () -> Unit
) {
    SoftCard(color = DesignTokens.WarmCard) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = allLocalSelected,
                        enabled = hasLocalBooks,
                        onCheckedChange = { onSelectAll() }
                    )
                    Text(if (allLocalSelected) "取消全选本地书" else "全选本地书")
                }
                Text("已选 $selectedCount 本", color = DesignTokens.SoftText)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "移出后不删除文件，再次导入同一文件可恢复到书城",
                    color = DesignTokens.SoftText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onRemove,
                    enabled = selectedCount > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB44A35)),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp,
                        disabledElevation = 0.dp
                    )
                ) {
                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = null)
                    Text("移出书城")
                }
            }
        }
    }
}

@Composable
private fun StoreHeroCard(
    featuredBooks: List<StoreBook>,
    onExploreClick: () -> Unit
) {
    SoftCard(color = DesignTokens.WarmCard) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "今日推荐",
                    modifier = Modifier
                        .background(Color(0xFFFFE8D0), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    color = DesignTokens.Accent,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "每一本好书\n都是一次探索",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text("精选优质图书，发现更多精彩", color = DesignTokens.SoftText)
                Text(
                    "立即探索",
                    modifier = Modifier
                        .background(Brush.horizontalGradient(listOf(DesignTokens.Accent, DesignTokens.AccentDark)), RoundedCornerShape(22.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onExploreClick
                        )
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                modifier = Modifier.padding(top = 28.dp),
                horizontalArrangement = Arrangement.spacedBy((-22).dp)
            ) {
                featuredBooks.forEachIndexed { index, book ->
                    BookCover(
                        title = book.title,
                        width = 58.dp,
                        height = 86.dp,
                        brush = Brush.verticalGradient(listOf(titleColor(book.title), Color(0xFF1C1B18))),
                        modifier = Modifier.padding(top = (index % 2 * 12).dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StoreCategoryScreen(
    onBack: () -> Unit,
    onBookClick: (String) -> Unit = {},
    onRemoteBookClick: (String) -> Unit = {},
    viewModel: StoreViewModel = viewModel(factory = StoreViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    val filter = uiState.filter
    val filteredBooks = uiState.filteredBooks
    val openBook: (StoreBook) -> Unit = { book ->
        when {
            book.kind == StoreItemKind.LOCAL -> onBookClick(book.id)
            book.downloadedLocalId != null -> onBookClick(book.downloadedLocalId)
            book.kind == StoreItemKind.OPDS -> onRemoteBookClick(book.id)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DesignTokens.AppBackground)
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                "筛选",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "重置",
                color = DesignTokens.Accent,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { viewModel.resetFilters() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = filter.query,
            onValueChange = viewModel::setQuery,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("搜索书名 / 作者 / 分类 / 来源") },
            shape = RoundedCornerShape(18.dp)
        )

        StoreSourceSegment(
            selected = filter.sourceId,
            sources = uiState.options.sourceOptions,
            onSelect = viewModel::setSourceFilter
        )

        StoreFilterChipRow(
            label = "格式",
            options = listOf(null to "全部格式") + uiState.options.formatOptions.map { it to it },
            selected = filter.format,
            onSelect = viewModel::setFormatFilter
        )

        StoreFilterChipRow(
            label = "分类",
            options = listOf(null to "全部") + uiState.options.categoryOptions.map { it to it },
            selected = filter.category,
            onSelect = viewModel::setCategoryFilter
        )

        StoreFilterChipRow(
            label = "排序",
            options = StoreSortOption.entries.map { it to it.label },
            selected = filter.sort,
            onSelect = viewModel::setSort
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                "结果 ${filteredBooks.size} 本",
                color = DesignTokens.SoftText,
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Icon(Icons.Default.FilterList, contentDescription = null)
                Text(filter.sort.label, style = MaterialTheme.typography.titleMedium)
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            gridItems(filteredBooks) { book ->
                CategoryBookCard(book, openBook)
            }
        }
    }
}

@Composable
fun StoreSearchScreen(
    onBack: () -> Unit,
    initialQuery: String? = null,
    onBookClick: (String) -> Unit = {},
    onRemoteBookClick: (String) -> Unit = {},
    onBackendBookClick: (Long) -> Unit = {},
    viewModel: StoreViewModel = viewModel(factory = StoreViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    val backendState by viewModel.backendSearchState.collectAsState()
    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank()) viewModel.setQuery(initialQuery)
    }
    val query = uiState.filter.query
    LaunchedEffect(query) { viewModel.searchBackend(query) }
    var sourceIndex by rememberSaveable { mutableIntStateOf(0) }
    val results = if (query.isBlank()) emptyList() else uiState.filteredBooks.filter { book ->
        when (sourceIndex) {
            1 -> book.kind == StoreItemKind.LOCAL
            2 -> book.kind == StoreItemKind.OPDS
            3 -> false
            else -> true
        }
    }
    val backendBooks = if (sourceIndex == 0 || sourceIndex == 3) backendState.books else emptyList()
    val openBook: (StoreBook) -> Unit = { book ->
        when {
            book.kind == StoreItemKind.LOCAL -> onBookClick(book.id)
            book.downloadedLocalId != null -> onBookClick(book.downloadedLocalId)
            else -> onRemoteBookClick(book.id)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(DesignTokens.AppBackground).padding(horizontal = DesignTokens.PagePadding, vertical = DesignTokens.Space16),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Space16)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space8)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.weight(1f),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotBlank()) Text("×", style = MaterialTheme.typography.headlineMedium, color = DesignTokens.SoftText,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null
                        ) { viewModel.setQuery("") }.padding(8.dp))
                },
                placeholder = { Text("搜索书籍、作者") },
                singleLine = true,
                shape = RoundedCornerShape(DesignTokens.RadiusLarge)
            )
        }
        SlidingSegmentedControl(
            options = listOf("全部", "本地", "OPDS", "后端"),
            selectedIndex = sourceIndex,
            onSelected = { sourceIndex = it }
        )
        if (query.isBlank()) {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DesignTokens.Space16)) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("最近搜索", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("清空", color = DesignTokens.Accent)
                    }
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space8)) {
                        items(listOf("三体", "刘慈欣", "科幻", "小王子")) { keyword ->
                            StoreChip(label = keyword, selected = false, onClick = { viewModel.setQuery(keyword) })
                        }
                    }
                }
                item { Text("🔥 热门搜索", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                item {
                    SoftCard(contentPadding = 0.dp) {
                        listOf(
                            "三体" to "科幻", "活着" to "文学", "百年孤独" to "魔幻现实主义", "小王子" to "童话",
                            "围城" to "文学", "解忧杂货店" to "小说", "1984" to "科幻", "平凡的世界" to "文学",
                            "追风筝的人" to "小说", "人类简史" to "历史"
                        ).forEachIndexed { index, (title, category) ->
                            PopularSearchRow(index + 1, title, category) { viewModel.setQuery(title) }
                        }
                    }
                }
            }
        } else if (results.isEmpty() && backendBooks.isEmpty() && !backendState.isLoading) {
            SoftCard(color = Color.White) { Text("没有找到与“$query”相关的内容", color = DesignTokens.SoftText) }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                results.groupBy { it.sourceId }.forEach { (_, sourceBooks) ->
                    item(key = "source-${sourceBooks.first().sourceId}") {
                        SearchSourceCard(sourceBooks, openBook, viewModel)
                    }
                }
                if (backendState.isLoading && (sourceIndex == 0 || sourceIndex == 3)) {
                    item("backend-loading") {
                        SoftCard { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() } }
                    }
                }
                if (backendBooks.isNotEmpty()) {
                    item("backend-results") {
                        BackendSearchCard(
                            books = backendBooks,
                            shelfBookIds = backendState.shelfBookIds,
                            coverUrl = { com.aibook.android.di.ServiceLocator.get(androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application).serverRepository.resolveCoverUrl(it.coverUrl) },
                            onBookClick = { it.id?.let(onBackendBookClick) },
                            onToggleShelf = viewModel::toggleBackendShelf
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackendSearchCard(
    books: List<com.aibook.android.core.network.api.dto.BookDTO>,
    shelfBookIds: Set<Long>,
    coverUrl: @Composable (com.aibook.android.core.network.api.dto.BookDTO) -> String?,
    onBookClick: (com.aibook.android.core.network.api.dto.BookDTO) -> Unit,
    onToggleShelf: (com.aibook.android.core.network.api.dto.BookDTO) -> Unit
) {
    SoftCard(contentPadding = DesignTokens.Space16) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("后端书库", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${books.size} 个结果", color = DesignTokens.SoftText)
        }
        books.forEach { book ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onBookClick(book) }.padding(top = DesignTokens.Space16),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space12),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BookCover(book.title, imageUri = coverUrl(book), width = 78.dp, height = 108.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DesignTokens.Space4)) {
                    Text(book.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(book.author ?: "未知作者", color = DesignTokens.SoftText, maxLines = 1)
                    SourceBadge("后端 · ${book.format ?: "在线"}")
                }
                Button(
                    onClick = { onToggleShelf(book) },
                    colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Accent),
                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp)
                ) { Text(if (book.id in shelfBookIds) "移出书架" else "+ 加入书架") }
            }
        }
    }
}

@Composable
private fun PopularSearchRow(rank: Int, title: String, category: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick
        ).padding(horizontal = DesignTokens.Space16, vertical = DesignTokens.Space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space12)
    ) {
        Box(
            Modifier.size(32.dp).background(
                if (rank <= 3) DesignTokens.Warning else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(DesignTokens.RadiusSmall)
            ), contentAlignment = Alignment.Center
        ) { Text(rank.toString(), color = if (rank <= 3) Color.White else DesignTokens.Accent, fontWeight = FontWeight.Bold) }
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space8), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                SourceBadge(category)
            }
            Text(if (title == "三体") "刘慈欣" else "热门作者", color = DesignTokens.SoftText)
        }
        Text("›", color = DesignTokens.SoftText, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun SearchSourceCard(
    books: List<StoreBook>,
    onBookClick: (StoreBook) -> Unit,
    viewModel: StoreViewModel
) {
    val first = books.first()
    SoftCard(contentPadding = DesignTokens.Space16) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(first.sourceName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${books.size} 个结果  ›", color = DesignTokens.SoftText)
        }
        books.forEach { book ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null
                ) { onBookClick(book) }.padding(top = DesignTokens.Space16),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space12),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BookCover(book.title, imageUri = book.coverUri, width = 78.dp, height = 108.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DesignTokens.Space4)) {
                    Text(book.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(book.author, color = DesignTokens.SoftText, maxLines = 1)
                    SourceBadge("${book.sourceName} · ${book.format}", book.sourceName)
                }
                Button(
                    onClick = {
                        if (book.kind == StoreItemKind.LOCAL) viewModel.addLocalBookToShelf(book)
                        else viewModel.downloadRemoteBook(book)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Accent),
                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                    enabled = !book.shelved && !book.isDownloaded
                ) { Text(if (book.shelved || book.isDownloaded) "已在书架" else "+ 加入书架") }
            }
        }
    }
}

@Composable
private fun SearchGroup(title: String, entries: List<Pair<String, Int>>, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries) { (label, count) ->
                StoreChip(label = "$label · $count", selected = false, onClick = { onSelect(label) })
            }
        }
    }
}

@Composable
private fun <T> StoreFilterChipRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(label, color = DesignTokens.SoftText, style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            items(options) { (value, optionLabel) ->
                val isSelected = value == selected
                Text(
                    optionLabel,
                    color = if (isSelected) DesignTokens.Accent else Color(0xFF2F2B26),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .background(
                            if (isSelected) DesignTokens.Accent.copy(alpha = 0.08f) else Color(0xFFF9F8F7),
                            RoundedCornerShape(22.dp)
                        )
                        .border(
                            1.dp,
                            if (isSelected) DesignTokens.Accent.copy(alpha = 0.42f) else DesignTokens.Hairline,
                            RoundedCornerShape(22.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(value) }
                        .padding(horizontal = 22.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun TextTabs(options: List<String>, selected: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        options.forEachIndexed { index, option ->
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text(
                    option,
                    color = if (index == selected) DesignTokens.Accent else DesignTokens.SoftText,
                    fontWeight = if (index == selected) FontWeight.Bold else FontWeight.Normal
                )
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .height(4.dp)
                        .width(36.dp)
                        .background(if (index == selected) DesignTokens.Accent else Color.Transparent, RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

@Composable
private fun CategoryBookCard(book: StoreBook, onBookClick: (StoreBook) -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onBookClick(book) },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DesignTokens.Hairline)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box {
                BookCover(
                    title = book.title,
                    modifier = Modifier.fillMaxWidth(),
                    width = 96.dp,
                    height = 132.dp,
                    imageUri = book.coverUri,
                    brush = Brush.verticalGradient(listOf(titleColor(book.title), Color(0xFF1C1B18)))
                )
                CategorySourceBadge(
                    text = book.sourceName,
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.BottomStart)
                        .padding(6.dp)
                )
            }
            Text(book.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(book.author, color = DesignTokens.SoftText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(book.format, color = DesignTokens.Accent)
        }
    }
}

@Composable
private fun CategorySourceBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .background(Color(0xFFFFF1D9), RoundedCornerShape(5.dp))
            .border(1.dp, Color(0xFFE6C99B), RoundedCornerShape(5.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = Color(0xFF62401D),
        style = MaterialTheme.typography.labelMedium
    )
}

@Composable
private fun StoreBookCard(
    book: StoreBook,
    downloading: Boolean,
    managementMode: Boolean,
    selected: Boolean,
    onBookClick: (StoreBook) -> Unit = {},
    onDownloadClick: (StoreBook) -> Unit = {},
    onLocalShelfClick: (StoreBook) -> Unit = {},
    onLocalShelfRemoveClick: (StoreBook) -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onBookClick(book) },
        shape = RoundedCornerShape(DesignTokens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = DesignTokens.SoftShadow)
    ) {
        Column {
            Box {
                BookCover(
                    title = book.title,
                    modifier = Modifier.fillMaxWidth(),
                    width = null,
                    height = 168.dp,
                    imageUri = book.coverUri,
                    brush = Brush.verticalGradient(listOf(titleColor(book.title), Color(0xFF1C1B18)))
                )
                Text(
                    text = "⋮",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(bottomStart = 14.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = DesignTokens.TextPrimary
                )
                if (managementMode && book.kind == StoreItemKind.LOCAL) {
                    StoreSelectionMark(
                        selected = selected,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.padding(DesignTokens.Space12),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = book.title,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = book.author,
                    color = DesignTokens.SoftText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "${book.format.uppercase()}  |  ${book.sourceName}",
                    color = DesignTokens.SoftText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall
                )
                val actionLabel = when {
                    book.kind == StoreItemKind.LOCAL && book.shelved -> "✓  已在书架"
                    book.kind == StoreItemKind.LOCAL -> "加入书架"
                    book.isDownloaded -> "✓  已下载"
                    downloading -> "下载中…"
                    else -> "下载到书架"
                }
                Button(
                    onClick = {
                        when {
                            book.kind == StoreItemKind.LOCAL && book.shelved -> onLocalShelfRemoveClick(book)
                            book.kind == StoreItemKind.LOCAL -> onLocalShelfClick(book)
                            !book.isDownloaded && !downloading -> onDownloadClick(book)
                        }
                    },
                    enabled = !managementMode && !downloading,
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    shape = RoundedCornerShape(DesignTokens.RadiusSmall),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (book.shelved || book.isDownloaded) DesignTokens.WarmCard else DesignTokens.Accent,
                        contentColor = if (book.shelved || book.isDownloaded) DesignTokens.AccentDark else Color.White,
                        disabledContainerColor = DesignTokens.WarmCard,
                        disabledContentColor = DesignTokens.SoftText
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Text(actionLabel, maxLines = 1, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun StoreSmallBookCard(
    book: StoreBook,
    downloading: Boolean,
    managementMode: Boolean,
    selected: Boolean,
    onBookClick: (StoreBook) -> Unit = {},
    onDownloadClick: (StoreBook) -> Unit = {},
    onLocalShelfClick: (StoreBook) -> Unit = {},
    onLocalShelfRemoveClick: (StoreBook) -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onBookClick(book) },
        shape = RoundedCornerShape(DesignTokens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Box {
                BookCover(
                    title = book.title,
                    modifier = Modifier.fillMaxWidth(),
                    width = null,
                    height = 140.dp,
                    imageUri = book.coverUri,
                    brush = Brush.verticalGradient(listOf(titleColor(book.title), Color(0xFF1C1B18)))
                )
                CoverSourceBadge(
                    text = "${CompactStoreRowLabels.format(book.format)}｜${CompactStoreRowLabels.source(book.kind)}",
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                )
                if (managementMode && book.kind == StoreItemKind.LOCAL) {
                    StoreSelectionMark(
                        selected = selected,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = book.title,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall
                    )
                    CompactStoreBookAction(
                        book = book,
                        downloading = downloading,
                        managementMode = managementMode,
                        onDownloadClick = onDownloadClick,
                        onLocalShelfClick = onLocalShelfClick,
                        onLocalShelfRemoveClick = onLocalShelfRemoveClick
                    )
                }
            }
        }
    }
}

@Composable
private fun StoreListItem(
    book: StoreBook,
    downloading: Boolean,
    managementMode: Boolean,
    selected: Boolean,
    onBookClick: (StoreBook) -> Unit = {},
    onDownloadClick: (StoreBook) -> Unit = {},
    onLocalShelfClick: (StoreBook) -> Unit = {},
    onLocalShelfRemoveClick: (StoreBook) -> Unit = {}
) {
    SoftCard(
        modifier = Modifier.fillMaxWidth().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onBookClick(book) },
        contentPadding = 10.dp
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (managementMode && book.kind == StoreItemKind.LOCAL) {
                StoreSelectionMark(selected = selected)
            }
            BookCover(
                title = book.title,
                width = 48.dp,
                height = 68.dp,
                imageUri = book.coverUri,
                brush = Brush.verticalGradient(listOf(titleColor(book.title), Color(0xFF1C1B18)))
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(book.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactStoreMetadataBadge(
                        text = "${CompactStoreRowLabels.format(book.format)}｜${CompactStoreRowLabels.source(book.kind)}",
                        color = DesignTokens.Accent
                    )
                    Text(
                        text = book.author,
                        modifier = Modifier.weight(1f),
                        color = DesignTokens.SoftText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            CompactStoreBookAction(
                book = book,
                downloading = downloading,
                managementMode = managementMode,
                onDownloadClick = onDownloadClick,
                onLocalShelfClick = onLocalShelfClick,
                onLocalShelfRemoveClick = onLocalShelfRemoveClick
            )
        }
    }
}

@Composable
private fun StoreBookAction(
    book: StoreBook,
    downloading: Boolean,
    managementMode: Boolean,
    onDownloadClick: (StoreBook) -> Unit,
    onLocalShelfClick: (StoreBook) -> Unit
) {
    if (book.kind == StoreItemKind.LOCAL) {
        if (managementMode) return
        val label = if (book.shelved) "已在书架" else "加入书架"
        Text(
            label,
            modifier = Modifier
                .background(DesignTokens.Accent.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                .clickable(
                    enabled = !book.shelved,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onLocalShelfClick(book) }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            color = if (book.shelved) MaterialTheme.colorScheme.onSurfaceVariant else DesignTokens.Accent,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium
        )
        return
    }

    val label = when {
        book.isDownloaded -> "已下载"
        downloading -> "下载中..."
        else -> "下载"
    }
    Text(
        label,
        modifier = Modifier
            .background(DesignTokens.Accent.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .clickable(
                enabled = !book.isDownloaded && !downloading,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDownloadClick(book) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = DesignTokens.Accent,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelMedium
    )
}

@Composable
private fun StoreCompactListItem(
    book: StoreBook,
    downloading: Boolean,
    managementMode: Boolean,
    selected: Boolean,
    onBookClick: (StoreBook) -> Unit = {},
    onDownloadClick: (StoreBook) -> Unit = {},
    onLocalShelfClick: (StoreBook) -> Unit = {},
    onLocalShelfRemoveClick: (StoreBook) -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onBookClick(book) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (managementMode && book.kind == StoreItemKind.LOCAL) {
            StoreSelectionMark(selected = selected)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = book.title,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactStoreMetadataBadge(
                    text = "${CompactStoreRowLabels.format(book.format)}｜${CompactStoreRowLabels.source(book.kind)}",
                    color = DesignTokens.Accent
                )
                Text(
                    text = book.author,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        CompactStoreBookAction(
            book = book,
            downloading = downloading,
            managementMode = managementMode,
            onDownloadClick = onDownloadClick,
            onLocalShelfClick = onLocalShelfClick,
            onLocalShelfRemoveClick = onLocalShelfRemoveClick
        )
    }
}

@Composable
private fun CompactStoreMetadataBadge(text: String, color: Color) {
    Text(
        text = text,
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        color = color,
        style = MaterialTheme.typography.labelSmall
    )
}

@Composable
private fun CompactStoreBookAction(
    book: StoreBook,
    downloading: Boolean,
    managementMode: Boolean,
    onDownloadClick: (StoreBook) -> Unit,
    onLocalShelfClick: (StoreBook) -> Unit,
    onLocalShelfRemoveClick: (StoreBook) -> Unit
) {
    if (book.kind != StoreItemKind.LOCAL) {
        StoreBookAction(
            book = book,
            downloading = downloading,
            managementMode = managementMode,
            onDownloadClick = onDownloadClick,
            onLocalShelfClick = onLocalShelfClick
        )
        return
    }
    if (managementMode) return

    val label = CompactStoreRowLabels.localShelf(book.shelved)
    Text(
        text = label.text,
        modifier = Modifier
            .semantics { contentDescription = label.contentDescription }
            .background(DesignTokens.Accent.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (book.shelved) onLocalShelfRemoveClick(book) else onLocalShelfClick(book)
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = if (book.shelved) MaterialTheme.colorScheme.onSurfaceVariant else DesignTokens.Accent,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelMedium
    )
}

@Composable
private fun StoreSelectionMark(
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(26.dp)
            .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(999.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = "选择",
            tint = if (selected) DesignTokens.Accent else DesignTokens.SoftText,
            modifier = Modifier.size(21.dp)
        )
    }
}

@Composable
private fun RecentUpdateCard(book: StoreBook) {
    Card(
        modifier = Modifier.width(168.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, DesignTokens.Hairline),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BookCover(
                title = book.title,
                width = 50.dp,
                height = 70.dp,
                imageUri = book.coverUri,
                brush = Brush.verticalGradient(listOf(titleColor(book.title), Color(0xFF1C1B18)))
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(book.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(book.author, color = DesignTokens.SoftText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(relativeTime(Instant.ofEpochSecond(book.updatedRank), Instant.now()), color = DesignTokens.SoftText, style = MaterialTheme.typography.bodySmall)
                SourceBadge(book.sourceName)
            }
        }
    }
}

private fun relativeTime(instant: Instant, now: Instant): String {
    val duration = Duration.between(instant, now)
    return when {
        duration.toMinutes() < 1 -> "刚刚更新"
        duration.toMinutes() < 60 -> "${duration.toMinutes()} 分钟前"
        duration.toHours() < 24 -> "${duration.toHours()} 小时前"
        duration.toDays() < 7 -> "${duration.toDays()} 天前"
        duration.toDays() < 30 -> "${duration.toDays() / 7} 周前"
        else -> "更早"
    }
}

private fun titleColor(title: String): Color {
    val colors = listOf(
        Color(0xFF253542), Color(0xFF4D171B), Color(0xFF222222),
        Color(0xFF9DBCC1), Color(0xFF6B4A2E), Color(0xFFD47A1F),
        Color(0xFF405B4B), Color(0xFF7C8FA6), Color(0xFF84613F),
        Color(0xFF1D5B76), Color(0xFF6FA8B6), Color(0xFF173B52)
    )
    return colors[title.hashCode().absoluteValue % colors.size]
}
