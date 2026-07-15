package com.aibook.android.feature.store

import android.content.Context
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
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.aibook.android.ui.design.CoverSourceBadge
import com.aibook.android.ui.design.DesignPage
import com.aibook.android.ui.design.DesignTokens
import com.aibook.android.ui.design.SoftCard
import com.aibook.android.ui.design.SourceBadge
import com.aibook.android.core.data.repository.DownloadStatus

@Composable
fun BookStoreScreen(
    onCategoryClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onBookClick: (String) -> Unit = {},
    onRemoteBookClick: (String) -> Unit = {},
    viewModel: StoreViewModel = viewModel(factory = StoreViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    val actionState by viewModel.actionState.collectAsState()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("store_prefs", Context.MODE_PRIVATE) }
    // 0=网格视图(3列), 1=带封面列表, 2=紧凑列表, 3=小网格视图(4列)
    var viewMode by remember { mutableIntStateOf(prefs.getInt("view_mode", 0)) }
    var showViewModeDialog by remember { mutableStateOf(false) }
    var managementMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var shelfRemovalBook by remember { mutableStateOf<StoreBook?>(null) }
    val filteredBooks = uiState.filteredBooks
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

    val viewModeIcon = when (viewMode) {
        0 -> Icons.Default.GridView
        1 -> Icons.AutoMirrored.Filled.FormatListBulleted
        else -> Icons.AutoMirrored.Filled.ViewList
    }

    DesignPage(
        title = if (managementMode) "已选 ${selectedLocalBooks.size} 本" else "",
        modifier = Modifier.fillMaxSize(),
        actions = {
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
                viewModeIcon,
                contentDescription = "切换视图",
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { showViewModeDialog = true }
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
            Text(
                "筛选",
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCategoryClick
                )
            )
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
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
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
//            StoreHeroCard(featuredBooks = featuredBooks, onExploreClick = onCategoryClick)
//            SectionHeader("全部浏览", "全部书籍 ${filteredBooks.size} ›")
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
                2 -> {
                    // 紧凑列表视图（无封面）
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) {
                        items(filteredBooks) { book ->
                            StoreCompactListItem(
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
                3 -> {
                    // 小网格视图（4列）
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) {
                        gridItems(filteredBooks) { book ->
                            StoreSmallBookCard(
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

    if (showViewModeDialog) {
        ViewModeDialog(
            currentMode = viewMode,
            onDismiss = { showViewModeDialog = false },
            onSelect = { mode ->
                viewMode = mode
                prefs.edit().putInt("view_mode", mode).apply()
                showViewModeDialog = false
            }
        )
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

@Composable
private fun ViewModeDialog(
    currentMode: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val viewModes = listOf(
        Triple(0, Icons.Default.GridView, "网格视图"),
        Triple(1, Icons.AutoMirrored.Filled.FormatListBulleted, "封面列表"),
        Triple(2, Icons.AutoMirrored.Filled.ViewList, "紧凑列表"),
        Triple(3, Icons.Default.GridView, "小网格视图")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择视图模式", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                viewModes.forEach { (mode, icon, label) ->
                    val isSelected = mode == currentMode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) DesignTokens.Accent.copy(alpha = 0.1f) else Color.Transparent,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelect(mode) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = if (isSelected) DesignTokens.Accent else DesignTokens.SoftText
                        )
                        Text(
                            label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) DesignTokens.Accent else Color.Unspecified
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (isSelected) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = DesignTokens.Accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
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
    val book = remember(books, bookId) {
        books.firstOrNull { it.id == bookId && it.kind == StoreItemKind.OPDS }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DesignTokens.AppBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                "书籍详情",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )
        }

        if (book == null) {
            SoftCard(color = Color.White) {
                Text("书籍不存在或数据源已停用", color = DesignTokens.SoftText)
            }
            return@Column
        }

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

        SoftCard(color = Color.White) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                BookCover(
                    title = book.title,
                    width = 92.dp,
                    height = 132.dp,
                    brush = Brush.verticalGradient(listOf(titleColor(book.title), Color(0xFF1C1B18)))
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(book.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text(book.author, color = DesignTokens.SoftText)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SourceBadge(book.sourceName)
                        SourceBadge(book.format)
                    }
                    Text(
                        if (book.isDownloaded) "已下载到本地书架" else "来自 OPDS 书城缓存",
                        color = DesignTokens.Accent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        SoftCard(color = Color.White) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("简介", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    book.summary?.takeIf { it.isNotBlank() } ?: "暂无简介",
                    color = DesignTokens.SoftText,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        SoftCard(color = Color.White) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("分类", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(book.categories) { category ->
                        StoreChip(label = category, selected = false, onClick = {})
                    }
                }
            }
        }

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
        Text(
            when {
                book.downloadedLocalId != null -> "打开本地书籍"
                task?.status == DownloadStatus.PAUSED -> "继续下载"
                task?.status == DownloadStatus.FAILED -> "重新下载"
                downloading -> "下载中 ${task?.progress ?: 0}%"
                else -> "下载到书架"
            },
            modifier = Modifier
                .fillMaxWidth()
                .background(DesignTokens.Accent, RoundedCornerShape(18.dp))
                .clickable(
                    enabled = !downloading,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    val localId = book.downloadedLocalId
                    if (localId != null) {
                        onOpenLocalBook(localId)
                    } else when (task?.status) {
                        DownloadStatus.PAUSED -> viewModel.resumeDownload(book.id)
                        DownloadStatus.FAILED -> viewModel.retryDownload(book.id)
                        else -> viewModel.downloadRemoteBook(book)
                    }
                }
                .padding(vertical = 15.dp),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.titleMedium
        )
    }
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
    onBookClick: (String) -> Unit = {},
    onRemoteBookClick: (String) -> Unit = {},
    viewModel: StoreViewModel = viewModel(factory = StoreViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    val query = uiState.filter.query
    val results = if (query.isBlank()) emptyList() else uiState.filteredBooks
    val authors = results.groupingBy { it.author }.eachCount().entries.sortedByDescending { it.value }.take(8)
    val categories = results.flatMap { it.categories }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(8)
    val sources = results.groupBy { it.sourceId }.mapNotNull { (id, books) ->
        books.firstOrNull()?.let { Triple(id, it.sourceName, books.size) }
    }.sortedByDescending { it.third }
    val openBook: (StoreBook) -> Unit = { book ->
        when {
            book.kind == StoreItemKind.LOCAL -> onBookClick(book.id)
            book.downloadedLocalId != null -> onBookClick(book.downloadedLocalId)
            else -> onRemoteBookClick(book.id)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(DesignTokens.AppBackground).padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
            Text("全局搜索", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        }
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("搜索书名、作者、分类或 OPDS 源") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp)
        )
        if (query.isBlank()) {
            SoftCard(color = Color.White) { Text("输入关键词后，将按作者、分类、OPDS 源和书籍分组展示结果。", color = DesignTokens.SoftText) }
        } else if (results.isEmpty()) {
            SoftCard(color = Color.White) { Text("没有找到与“$query”相关的内容", color = DesignTokens.SoftText) }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                if (authors.isNotEmpty()) item {
                    SearchGroup("作者", authors.map { it.key to it.value }) { author -> viewModel.setQuery(author) }
                }
                if (categories.isNotEmpty()) item {
                    SearchGroup("分类", categories.map { it.key to it.value }) { category -> viewModel.setCategoryFilter(category) }
                }
                if (sources.isNotEmpty()) item {
                    SearchGroup("OPDS 源", sources.map { it.second to it.third }) { sourceName ->
                        sources.firstOrNull { it.second == sourceName }?.let { viewModel.setSourceFilter(it.first) }
                    }
                }
                item {
                    Text("书籍 · ${results.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                items(results, key = { "${it.kind}-${it.id}" }) { book ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { openBook(book) },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DesignTokens.Hairline)
                    ) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            BookCover(title = book.title, imageUri = book.coverUri, width = 58.dp, height = 82.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(book.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(book.author, color = DesignTokens.SoftText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${book.sourceName} · ${book.format}", color = DesignTokens.Accent)
                            }
                        }
                    }
                }
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Box {
                BookCover(
                    title = book.title,
                    modifier = Modifier.fillMaxWidth(),
                    width = null,
                    height = 180.dp,
                    imageUri = book.coverUri,
                    brush = Brush.verticalGradient(listOf(titleColor(book.title), Color(0xFF1C1B18)))
                )
                CoverSourceBadge(
                    text = "${CompactStoreRowLabels.format(book.format)}｜${CompactStoreRowLabels.source(book.kind)}",
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
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
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
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
                        overflow = TextOverflow.Ellipsis
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
