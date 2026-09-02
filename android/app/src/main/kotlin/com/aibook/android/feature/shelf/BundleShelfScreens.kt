package com.aibook.android.feature.shelf

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aibook.android.core.model.LocalBook
import com.aibook.android.core.model.ShelfFolderSelection
import com.aibook.android.core.model.ShelfSortOption
import com.aibook.android.ui.design.BookCover
import com.aibook.android.ui.design.DesignTokens
import com.aibook.android.ui.design.SlidingSegmentedControl
import com.aibook.android.ui.design.SoftCard
import com.aibook.android.ui.design.WarmProgress
import java.time.Duration
import java.time.Instant

@Composable
fun ShelfSortFilterScreen(onBack: () -> Unit, onApplied: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(ShelfPreferences.FILE_NAME, Context.MODE_PRIVATE) }
    val initial = prefs.getString(ShelfPreferences.KEY_SORT_OPTION, ShelfSortOption.RECENT_READ.name)
        ?.let { runCatching { ShelfSortOption.valueOf(it) }.getOrNull() }
        ?: ShelfSortOption.RECENT_READ
    var selected by remember { mutableStateOf(initial) }
    var onlyUnread by remember { mutableStateOf(prefs.getBoolean(ShelfPreferences.KEY_FILTER_UNREAD, false)) }
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(DesignTokens.PagePadding),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Space16)
    ) {
        BundleHeaderRow("排序与筛选", onBack)
        SoftCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space12)) {
                Icon(Icons.AutoMirrored.Filled.Sort, null, tint = DesignTokens.Accent)
                Text("排序方式", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            ShelfSortOption.entries.forEach { option ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { selected = option }.padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(option.label)
                    if (selected == option) Icon(Icons.Default.Check, null, tint = DesignTokens.Accent)
                }
            }
        }
        SoftCard {
            Text("阅读状态", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            SlidingSegmentedControl(
                options = listOf("全部", "未读"),
                selectedIndex = if (onlyUnread) 1 else 0,
                onSelected = { onlyUnread = it == 1 },
                modifier = Modifier.padding(top = DesignTokens.Space12)
            )
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = {
                prefs.edit()
                    .putString(ShelfPreferences.KEY_SORT_OPTION, selected.name)
                    .putBoolean(ShelfPreferences.KEY_FILTER_UNREAD, onlyUnread)
                    .apply()
                onApplied()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Accent),
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp)
        ) { Text("应用", fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun ShelfFoldersScreen(
    onBack: () -> Unit,
    onFolderClick: (String) -> Unit,
    onCreateClick: () -> Unit,
    viewModel: ShelfViewModel = viewModel(factory = ShelfViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BundlePageHeader("书架文件夹", onBack, onCreateClick)
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(DesignTokens.PagePadding),
        contentPadding = PaddingValues(top = 72.dp, bottom = DesignTokens.Space24),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Space12)
    ) {
        item {
            SoftCard(color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space12)) {
                    Icon(Icons.Default.Folder, null, tint = DesignTokens.Accent)
                    Text("共 ${state.folders.size} 个文件夹 · ${state.books.size} 本书", color = DesignTokens.SoftText)
                }
            }
        }
        items(state.folders, key = { it.id }) { folder ->
            SoftCard(modifier = Modifier.clickable { onFolderClick(folder.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space16)) {
                    Box(
                        modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(DesignTokens.RadiusMedium)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Folder, null, tint = DesignTokens.Accent, modifier = Modifier.size(32.dp)) }
                    Column(Modifier.weight(1f)) {
                        Text(folder.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("${state.folderCounts[folder.id] ?: 0} 本", color = DesignTokens.SoftText)
                    }
                    Text("›", color = DesignTokens.SoftText, style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
    }
}

@Composable
fun ShelfFolderDetailScreen(
    folderId: String,
    onBack: () -> Unit,
    onBookClick: (String) -> Unit,
    viewModel: ShelfViewModel = viewModel(factory = ShelfViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(folderId) { viewModel.selectFolder(ShelfFolderSelection.Folder(folderId)) }
    val folder = state.folders.firstOrNull { it.id == folderId }
    val books = state.books.filter { it.folderId == folderId }
    var viewMode by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(DesignTokens.PagePadding)) {
        BundleHeaderRow(folder?.name ?: "文件夹", onBack)
        Text("${books.size} 本", color = DesignTokens.SoftText)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = DesignTokens.Space16),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("＋ 添加书籍", color = DesignTokens.Accent, fontWeight = FontWeight.Bold)
            SlidingSegmentedControl(
                options = listOf("卡片", "列表"),
                selectedIndex = viewMode,
                onSelected = { viewMode = it },
                modifier = Modifier.width(180.dp)
            )
        }
        if (viewMode == 0) {
            BundleBookGrid(books, onBookClick, Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DesignTokens.Space8)) {
                items(books, key = { it.id }) { book -> BundleBookListItem(book) { onBookClick(book.id) } }
            }
        }
    }
}

@Composable
fun CreateShelfFolderScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: ShelfViewModel = viewModel(factory = ShelfViewModel.Factory)
) {
    var name by remember { mutableStateOf("") }
    var colorIndex by remember { mutableIntStateOf(0) }
    val colors = listOf(Color(0xFFD8B06A), Color(0xFF78A9E6), Color(0xFF83B879), Color(0xFFF0B64D), Color(0xFF9A86C8), Color(0xFFE97E6E))
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(DesignTokens.PagePadding),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Space24)
    ) {
        BundleHeaderRow("新建文件夹", onBack)
        Text("文件夹仅用于整理书架中的书籍", color = DesignTokens.SoftText, modifier = Modifier.align(Alignment.CenterHorizontally))
        SoftCard {
            Text("文件夹名称", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 20) name = it },
                modifier = Modifier.fillMaxWidth().padding(top = DesignTokens.Space12),
                placeholder = { Text("输入文件夹名称") },
                supportingText = { Text("${name.length}/20") },
                singleLine = true,
                shape = RoundedCornerShape(DesignTokens.RadiusMedium)
            )
            Text("封面色", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = DesignTokens.Space16))
            Row(Modifier.fillMaxWidth().padding(top = DesignTokens.Space12), horizontalArrangement = Arrangement.SpaceBetween) {
                colors.forEachIndexed { index, color ->
                    Box(
                        modifier = Modifier.size(42.dp).background(color, CircleShape)
                            .border(if (index == colorIndex) 3.dp else 0.dp, MaterialTheme.colorScheme.surface, CircleShape)
                            .clickable { colorIndex = index },
                        contentAlignment = Alignment.Center
                    ) { if (index == colorIndex) Icon(Icons.Default.Check, null, tint = Color.White) }
                }
            }
            Text("预览", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = DesignTokens.Space24))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space12)) {
                Box(Modifier.size(64.dp).background(colors[colorIndex].copy(alpha = 0.2f), RoundedCornerShape(DesignTokens.RadiusMedium)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Folder, null, tint = colors[colorIndex], modifier = Modifier.size(38.dp))
                }
                Text(name.ifBlank { "文件夹名称" }, fontWeight = FontWeight.SemiBold)
            }
        }
        Button(
            onClick = { viewModel.createFolder(name); onCreated() },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Accent),
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp)
        ) { Text("创建") }
    }
}

@Composable
fun RecentReadingScreen(
    onBack: () -> Unit,
    onBookClick: (String) -> Unit,
    viewModel: ShelfViewModel = viewModel(factory = ShelfViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val books = state.books.filter { it.lastReadAt != null }.sortedByDescending { it.lastReadAt }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(DesignTokens.PagePadding)) {
        BundleHeaderRow("最近阅读", onBack)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DesignTokens.Space12)) {
            items(books, key = { it.id }) { book ->
                SoftCard(modifier = Modifier.clickable { onBookClick(book.id) }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space16), verticalAlignment = Alignment.CenterVertically) {
                        BookCover(book.title, width = 76.dp, height = 108.dp, imageUri = book.coverUri)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DesignTokens.Space8)) {
                            Text(book.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(book.author ?: "未知作者", color = DesignTokens.SoftText)
                            WarmProgress(book.progress.percent, Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space8), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.History, null, tint = DesignTokens.SoftText, modifier = Modifier.size(18.dp))
                                Text(relativeTime(book.lastReadAt), color = DesignTokens.SoftText, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text("${(book.progress.percent * 100).toInt()}%", color = DesignTokens.Accent)
                    }
                }
            }
        }
    }
}

@Composable
fun BookSourcesScreen(
    bookId: String,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    viewModel: ShelfViewModel = viewModel(factory = ShelfViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val current = state.books.firstOrNull { it.id == bookId }
    val versions = current?.let { book -> state.books.filter { it.title.equals(book.title, true) } }.orEmpty()
    var selectedId by remember(bookId) { mutableStateOf(bookId) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(DesignTokens.PagePadding)) {
        BundleHeaderRow("可用版本 / 来源", onBack)
        current?.let {
            Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space16), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = DesignTokens.Space16)) {
                BookCover(it.title, width = 76.dp, height = 108.dp, imageUri = it.coverUri)
                Column { Text(it.title, style = MaterialTheme.typography.displaySmall); Text(it.author ?: "未知作者", color = DesignTokens.SoftText) }
            }
        }
        Text("检测到以下可用版本，请选择要阅读的来源", color = DesignTokens.SoftText)
        LazyColumn(Modifier.weight(1f).padding(top = DesignTokens.Space16), verticalArrangement = Arrangement.spacedBy(DesignTokens.Space12)) {
            items(versions, key = { it.id }) { book ->
                SoftCard(
                    modifier = Modifier.border(if (book.id == selectedId) 1.dp else 0.dp, DesignTokens.Accent, RoundedCornerShape(DesignTokens.CardRadius)).clickable { selectedId = book.id }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space16)) {
                        Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(DesignTokens.RadiusMedium)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Folder, null, tint = DesignTokens.Accent)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(if (book.id == bookId) "本地文件" else book.format.displayName, fontWeight = FontWeight.Bold)
                            Text("${book.format.displayName} · 可离线阅读", color = DesignTokens.SoftText)
                        }
                        if (book.id == selectedId) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.background(DesignTokens.Accent, CircleShape).padding(6.dp))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, null, tint = DesignTokens.SoftText, modifier = Modifier.size(16.dp)); Text(" 所有来源均受保护，仅您可见", color = DesignTokens.SoftText)
        }
        Button(
            onClick = { onSelect(selectedId) },
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = DesignTokens.Space8),
            colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Accent),
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp)
        ) { Text("使用该版本") }
    }
}

@Composable
private fun BundlePageHeader(title: String, onBack: () -> Unit, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = DesignTokens.PagePadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        IconButton(onClick = onAction) { Icon(Icons.Default.Add, "新建") }
    }
}

@Composable
private fun BundleHeaderRow(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BundleBookGrid(books: List<LocalBook>, onBookClick: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space12),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Space16)
    ) {
        items(books, key = { it.id }) { book ->
            Column(Modifier.clickable { onBookClick(book.id) }, verticalArrangement = Arrangement.spacedBy(DesignTokens.Space8)) {
                BookCover(book.title, width = null, height = 148.dp, imageUri = book.coverUri)
                Text(book.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(book.author ?: "未知作者", color = DesignTokens.SoftText, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                WarmProgress(book.progress.percent, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun BundleBookListItem(book: LocalBook, onClick: () -> Unit) {
    SoftCard(modifier = Modifier.clickable(onClick = onClick), contentPadding = DesignTokens.Space12) {
        Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space12), verticalAlignment = Alignment.CenterVertically) {
            BookCover(book.title, width = 64.dp, height = 92.dp, imageUri = book.coverUri)
            Column(Modifier.weight(1f)) {
                Text(book.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(book.author ?: "未知作者", color = DesignTokens.SoftText)
                Text("${(book.progress.percent * 100).toInt()}%", color = DesignTokens.Accent)
            }
        }
    }
}

private fun relativeTime(instant: Instant?): String {
    if (instant == null) return "尚未阅读"
    val hours = Duration.between(instant, Instant.now()).toHours().coerceAtLeast(0)
    return when {
        hours < 1 -> "刚刚"
        hours < 24 -> "$hours 小时前"
        else -> "${hours / 24} 天前"
    }
}
