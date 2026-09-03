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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.aibook.android.core.network.api.dto.BookDTO
import com.aibook.android.di.ServiceLocator
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
    var favoriteOnly by remember { mutableStateOf(prefs.getBoolean("filter_favorite", false)) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(DesignTokens.PagePadding).padding(bottom = 76.dp),
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
                    Text(option.label, fontWeight = FontWeight.Medium)
                    RadioButton(
                        selected = selected == option,
                        onClick = { selected = option },
                        colors = RadioButtonDefaults.colors(selectedColor = DesignTokens.Accent)
                    )
                }
            }
            }
            SoftCard {
                Text("筛选", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                FilterSettingRow(Icons.Default.Storefront, "来源", "全部")
                FilterSettingRow(Icons.Default.Description, "格式", "全部")
                FilterSettingRow(Icons.Default.Download, "是否下载", "全部")
                FilterSettingRow(Icons.Default.Book, "是否已读", if (onlyUnread) "未读" else "全部") { onlyUnread = !onlyUnread }
                FilterSettingRow(Icons.Default.FavoriteBorder, "是否收藏", if (favoriteOnly) "已收藏" else "全部") { favoriteOnly = !favoriteOnly }
            }
        }
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = DesignTokens.PagePadding, vertical = DesignTokens.Space12),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space12)
        ) {
            OutlinedButton(
                onClick = { selected = ShelfSortOption.RECENT_READ; onlyUnread = false; favoriteOnly = false },
                modifier = Modifier.weight(1f).height(56.dp)
            ) { Text("重置", fontWeight = FontWeight.Bold) }
            Button(
                onClick = {
                    prefs.edit()
                        .putString(ShelfPreferences.KEY_SORT_OPTION, selected.name)
                        .putBoolean(ShelfPreferences.KEY_FILTER_UNREAD, onlyUnread)
                        .putBoolean("filter_favorite", favoriteOnly)
                        .apply()
                    onApplied()
                },
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Accent),
                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp)
            ) { Text("确定", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun FilterSettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space12)
    ) {
        Icon(icon, contentDescription = null, tint = DesignTokens.Accent)
        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Text(value, color = DesignTokens.SoftText)
        Text("›", color = DesignTokens.SoftText, style = MaterialTheme.typography.titleLarge)
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
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BundlePageHeader("书架文件夹", onBack, onCreateClick)
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = DesignTokens.PagePadding),
            contentPadding = PaddingValues(bottom = DesignTokens.Space24),
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
}

@Composable
fun ShelfFolderDetailScreen(
    folderId: String,
    onBack: () -> Unit,
    onBookClick: (String) -> Unit,
    onAddBooks: () -> Unit = {},
    viewModel: ShelfViewModel = viewModel(factory = ShelfViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(folderId) { viewModel.selectFolder(ShelfFolderSelection.Folder(folderId)) }
    val folder = state.folders.firstOrNull { it.id == folderId }
    val books = state.books.filter { it.folderId == folderId }
    var viewMode by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = DesignTokens.PagePadding)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {}) { Icon(Icons.Default.Search, "搜索") }
            IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, "更多") }
        }
        Text(folder?.name ?: "文件夹", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
        Text("${books.size} 本", color = DesignTokens.SoftText, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = DesignTokens.Space8))
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = DesignTokens.Space24),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onAddBooks,
                shape = RoundedCornerShape(DesignTokens.RadiusLarge),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Default.Add, null)
                Text("添加书籍", modifier = Modifier.padding(start = DesignTokens.Space8), fontWeight = FontWeight.Bold)
            }
            SlidingSegmentedControl(
                options = listOf("▦", "☷"),
                selectedIndex = viewMode,
                onSelected = { viewMode = it },
                modifier = Modifier.width(112.dp)
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
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Text("最近阅读", modifier = Modifier.weight(1f), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, "更多") }
        }
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
            if (books.isNotEmpty()) {
                item {
                    TextButton(onClick = viewModel::clearReadingHistory, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Delete, null)
                        Text("清除全部记录")
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
    val context = LocalContext.current
    val locator = remember { ServiceLocator.get(context.applicationContext as android.app.Application) }
    val opdsEntries by locator.opdsCatalogCacheRepository.observeEntries().collectAsStateWithLifecycle(initialValue = emptyList())
    val current = state.books.firstOrNull { it.id == bookId }
    val backendBooks by androidx.compose.runtime.produceState<List<BookDTO>>(emptyList(), current?.title) {
        value = current?.title?.let { title ->
            locator.serverRepository.searchBooks(title).getOrNull()?.content
                ?.filter { it.title.equals(title, ignoreCase = true) }
                .orEmpty()
        }.orEmpty()
    }
    val versions = current?.let { book ->
        val local = state.books.filter { it.title.equals(book.title, true) }.map {
            SourceVersion(it.id, "本地文件", it.format.displayName, "可离线阅读", it.coverUri)
        }
        val opds = opdsEntries.filter { it.title.equals(book.title, true) }.map {
            SourceVersion("opds:${it.id}", it.sourceName, it.format, "OPDS · 可下载", null)
        }
        val backend = backendBooks.map {
            SourceVersion("backend:${it.id}", "我的书库", it.format ?: "在线", "后端服务 · 在线阅读", locator.serverRepository.resolveCoverUrl(it.coverUrl))
        }
        (local + opds + backend).distinctBy { it.id }
    }.orEmpty()
    var selectedId by remember(bookId) { mutableStateOf(bookId) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = DesignTokens.PagePadding)) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Text("可用版本 / 来源", modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(48.dp))
        }
        current?.let {
            Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space24), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = DesignTokens.Space24)) {
                BookCover(it.title, width = 108.dp, height = 154.dp, imageUri = it.coverUri)
                Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Space12)) {
                    Text(it.title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(it.author ?: "未知作者", color = DesignTokens.SoftText, style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space8)) {
                        it.tags.take(2).ifEmpty { listOf(it.format.displayName) }.forEach { tag ->
                            Text(tag, modifier = Modifier.background(DesignTokens.WarmCard, RoundedCornerShape(DesignTokens.RadiusLarge)).padding(horizontal = 12.dp, vertical = 7.dp), color = DesignTokens.Accent)
                        }
                    }
                }
            }
        }
        Text("检测到以下可用版本，请选择要阅读的来源", color = DesignTokens.SoftText, style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.weight(1f).padding(top = DesignTokens.Space16), verticalArrangement = Arrangement.spacedBy(DesignTokens.Space12)) {
            items(versions, key = { it.id }) { version ->
                SoftCard(
                    modifier = Modifier.border(if (version.id == selectedId) 1.dp else 0.dp, DesignTokens.Accent, RoundedCornerShape(DesignTokens.CardRadius)).clickable { selectedId = version.id }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space16)) {
                        Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(DesignTokens.RadiusMedium)), contentAlignment = Alignment.Center) {
                            Icon(
                                when {
                                    version.id.startsWith("backend:") -> Icons.Default.Cloud
                                    version.id.startsWith("opds:") -> Icons.Default.Storage
                                    else -> Icons.Default.Folder
                                },
                                null,
                                tint = DesignTokens.Accent
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space8)) {
                                Text(version.source, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(
                                    when {
                                        version.id.startsWith("backend:") -> "云端"
                                        version.id.startsWith("opds:") -> "可下载"
                                        else -> "已下载"
                                    },
                                    modifier = Modifier.background(DesignTokens.WarmCard, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                                    color = DesignTokens.Accent,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            Text("${version.format} · ${version.detail}", color = DesignTokens.SoftText)
                        }
                        if (version.id == selectedId) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.background(DesignTokens.Accent, CircleShape).padding(6.dp))
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

private data class SourceVersion(
    val id: String,
    val source: String,
    val format: String,
    val detail: String,
    val coverUri: String?
)

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
