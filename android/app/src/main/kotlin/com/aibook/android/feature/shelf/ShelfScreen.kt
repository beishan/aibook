package com.aibook.android.feature.shelf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aibook.android.core.model.LocalBook
import com.aibook.android.core.model.ShelfFolder
import com.aibook.android.core.model.ShelfFolderSelection
import com.aibook.android.feature.importer.LocalBookImportViewModel
import com.aibook.android.feature.importer.rememberLocalBookImportLauncher
import com.aibook.android.feature.importer.supportedBookMimeTypes
import com.aibook.android.ui.design.BookCover
import com.aibook.android.ui.design.DesignPage
import com.aibook.android.ui.design.DesignTokens
import com.aibook.android.ui.design.SoftCard
import com.aibook.android.ui.design.SectionHeader
import com.aibook.android.ui.design.SlidingSegmentedControl
import com.aibook.android.ui.design.WarmProgress

@Composable
fun ShelfScreen(
    onBookClick: (String) -> Unit,
    onReadClick: (String) -> Unit,
    onRemoteReadClick: (Long) -> Unit = {},
    onFoldersClick: () -> Unit = {},
    onRecentReadingClick: () -> Unit = {},
    onSortClick: () -> Unit = {},
    viewModel: ShelfViewModel = viewModel(factory = ShelfViewModel.Factory),
    importViewModel: LocalBookImportViewModel = viewModel(factory = LocalBookImportViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshRemoteShelf() }
    val importState by importViewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLocalBookImportLauncher { uris ->
        importViewModel.importBooks(uris)
    }
    var onlyUnread by remember { mutableStateOf(false) }
    var sourceFilter by remember { mutableIntStateOf(0) }
    val visibleBooks = state.filteredBooks.filter { book ->
        val sourceMatches = when (sourceFilter) {
            1 -> book.isLocalSource()
            2 -> book.isOpdsSource()
            3 -> book.isServerBook()
            else -> true
        }
        sourceMatches && (!onlyUnread || book.progress.percent <= 0f)
    }
    val visibleLocalBooks = visibleBooks.filterNot { it.isServerBook() }
    val featuredBook = visibleBooks.firstOrNull()
    val hasBooks = state.books.isNotEmpty() || state.remoteBooks.isNotEmpty()
    val allVisibleSelected = visibleLocalBooks.isNotEmpty() && visibleLocalBooks.all { it.id in state.selectedIds }
    val selectedFavorite = state.selectedBooks.isNotEmpty() && state.selectedBooks.all { it.favorite }
    var showMoveDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val prefs = remember(context) { ShelfPreferences.preferences(context) }
    var viewMode by remember { mutableIntStateOf(prefs.getInt("reading_view_mode", 0).coerceIn(0, 1)) }
    var showContinueReadingCards by remember {
        mutableStateOf(ShelfPreferences.showContinueReadingCards(prefs))
    }
    var showSearch by remember { mutableStateOf(state.query.isNotBlank()) }
    var showTopMenu by remember { mutableStateOf(false) }

    LaunchedEffect(prefs) {
        val savedSort = prefs.getString(ShelfPreferences.KEY_SORT_OPTION, null)
            ?.let { value -> runCatching { com.aibook.android.core.model.ShelfSortOption.valueOf(value) }.getOrNull() }
        if (savedSort != null) viewModel.setSortOption(savedSort)
        onlyUnread = prefs.getBoolean(ShelfPreferences.KEY_FILTER_UNREAD, false)
    }

    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
            if (key == ShelfPreferences.KEY_SHOW_CONTINUE_READING_CARDS) {
                showContinueReadingCards = ShelfPreferences.showContinueReadingCards(preferences)
            } else if (key == ShelfPreferences.KEY_SORT_OPTION) {
                preferences.getString(key, null)
                    ?.let { value -> runCatching { com.aibook.android.core.model.ShelfSortOption.valueOf(value) }.getOrNull() }
                    ?.let(viewModel::setSortOption)
            } else if (key == ShelfPreferences.KEY_FILTER_UNREAD) {
                onlyUnread = preferences.getBoolean(key, false)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    DesignPage(
        title = if (state.managementMode) "已选 ${state.selectedIds.size} 本" else "我的书架",
        modifier = Modifier.fillMaxSize(),
        actions = {
            Icon(
                if (showSearch) Icons.Default.Close else Icons.Default.Search,
                contentDescription = if (showSearch) "关闭搜索" else "搜索书架",
                modifier = Modifier.clickable { showSearch = !showSearch }
            )
            if (state.managementMode) {
                Text("完成", modifier = Modifier.clickable { viewModel.setManagementMode(false) })
            } else {
                Box {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "更多",
                        modifier = Modifier.clickable { showTopMenu = true }
                    )
                    DropdownMenu(expanded = showTopMenu, onDismissRequest = { showTopMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("排序与筛选") },
                            onClick = { showTopMenu = false; onSortClick() }
                        )
                        DropdownMenuItem(
                            text = { Text("最近阅读") },
                            onClick = { showTopMenu = false; onRecentReadingClick() }
                        )
                        DropdownMenuItem(
                            text = { Text("批量管理") },
                            onClick = { showTopMenu = false; viewModel.setManagementMode(true) }
                        )
                    }
                }
            }
        }
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(DesignTokens.Space16)) {
            if (!state.managementMode) {
                item {
                    SlidingSegmentedControl(
                        options = listOf("全部", "本地", "OPDS", "后端"),
                        selectedIndex = sourceFilter,
                        onSelected = { sourceFilter = it }
                    )
                }
            }
            if (showSearch) {
                item {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        placeholder = { Text("搜索书名或作者") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
            if (hasBooks) {
                if (showContinueReadingCards && featuredBook != null && !state.managementMode) {
                    item {
                        ContinueReadingHero(
                            book = featuredBook,
                            onReadClick = {
                                featuredBook.serverBookId()?.let(onRemoteReadClick) ?: onReadClick(featuredBook.id)
                            }
                        )
                    }
                }
                item { SectionHeader("文件夹", "查看全部 ›", onFoldersClick) }
                item {
                    ShelfFolderFilterRow(
                        folders = state.folders,
                        folderCounts = state.folderCounts,
                        selection = state.folderSelection,
                        favoriteCount = state.books.count { it.favorite } + state.remoteBooks.count { it.favorite },
                        unfiledCount = state.books.count { it.folderId == null },
                        totalCount = state.books.size + state.remoteBooks.size,
                        onSelect = viewModel::selectFolder
                    )
                }
            }
            if (state.managementMode) {
                item {
                    ShelfManagementBar(
                        selectedCount = state.selectedIds.size,
                        allVisibleSelected = allVisibleSelected,
                        favoriteSelected = selectedFavorite,
                        hasSelection = state.selectedIds.isNotEmpty(),
                        onSelectAll = {
                            if (allVisibleSelected) viewModel.clearSelection() else viewModel.selectAllVisible()
                        },
                        onFavorite = { viewModel.setSelectedFavorite(!selectedFavorite) },
                        onMove = { showMoveDialog = true },
                        onRemove = viewModel::removeSelectedFromShelf
                    )
                }
            }
            if (state.isLoading) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            if (visibleBooks.isNotEmpty()) {
                if (!state.managementMode) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("全部书籍", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            SlidingSegmentedControl(
                                options = listOf("▦", "☷"),
                                selectedIndex = viewMode.coerceIn(0, 1),
                                onSelected = { mode ->
                                    viewMode = mode
                                    prefs.edit().putInt("reading_view_mode", mode).apply()
                                },
                                modifier = Modifier.width(112.dp)
                            )
                        }
                    }
                }
                item {
                    ReadingBooksView(
                        books = visibleBooks,
                        viewMode = viewMode,
                        managementMode = state.managementMode,
                        selectedIds = state.selectedIds,
                        onBookClick = { book ->
                            if (state.managementMode) {
                                if (!book.isServerBook()) viewModel.toggleBookSelection(book.id)
                            } else {
                                book.serverBookId()?.let(onRemoteReadClick) ?: onBookClick(book.id)
                            }
                        },
                        onReadClick = { book ->
                            book.serverBookId()?.let(onRemoteReadClick) ?: onReadClick(book.id)
                        },
                        onSelect = { if (!it.isServerBook()) viewModel.toggleBookSelection(it.id) },
                        onFavoriteClick = { book ->
                            book.serverBookId()?.let(viewModel::toggleRemoteFavorite)
                                ?: viewModel.setFavorite(book.id, !book.favorite)
                        },
                        onRemoveClick = { book ->
                            book.serverBookId()?.let(viewModel::removeRemoteFromShelf)
                                ?: viewModel.toggleShelved(book.id, false)
                        },
                        onLoadMore = viewModel::loadNextPage
                    )
                }
            }
            if (!hasBooks) {
                item {
                    ImportLocalBookCard(
                        message = importState.message,
                        isImporting = importState.isImporting,
                        onImportClick = { picker.launch(supportedBookMimeTypes) }
                    )
                }
            } else if (visibleBooks.isEmpty()) {
                item {
                    SoftCard(color = DesignTokens.WarmCard) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "没有匹配的书籍",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text("换个关键词再试试", color = DesignTokens.SoftText)
                        }
                    }
                }
            }
        }
    }

    if (showMoveDialog) {
        MoveToFolderDialog(
            folders = state.folders,
            selectedCount = state.selectedIds.size,
            onDismiss = { showMoveDialog = false },
            onMoveToUnfiled = {
                viewModel.moveSelectedToFolder(null)
                showMoveDialog = false
            },
            onMoveToFolder = {
                viewModel.moveSelectedToFolder(it)
                showMoveDialog = false
            },
            onCreateFolder = {
                viewModel.createFolderAndMoveSelected(it)
                showMoveDialog = false
            }
        )
    }

}

@Composable
private fun ReadingBooksView(
    books: List<LocalBook>,
    viewMode: Int,
    managementMode: Boolean,
    selectedIds: Set<String>,
    onBookClick: (LocalBook) -> Unit,
    onReadClick: (LocalBook) -> Unit,
    onSelect: (LocalBook) -> Unit,
    onFavoriteClick: (LocalBook) -> Unit,
    onRemoveClick: (LocalBook) -> Unit,
    onLoadMore: () -> Unit
) {
    when (viewMode) {
        0 -> LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Space16),
            modifier = Modifier.fillMaxWidth().height((((books.size + 2) / 3).coerceAtLeast(1) * 244).dp)
        ) {
            gridItems(books, key = { it.id }) { book ->
                if (book.id == books.lastOrNull()?.id) LaunchedEffect(book.id) { onLoadMore() }
                ReadingBookCard(
                    book = book,
                    managementMode = managementMode && !book.isServerBook(),
                    selected = book.id in selectedIds,
                    onCoverClick = { onBookClick(book) },
                    onReadClick = { onReadClick(book) },
                    onSelect = { onSelect(book) },
                    onFavoriteClick = { onFavoriteClick(book) },
                    onRemoveClick = { onRemoveClick(book) }
                )
            }
        }

        1 -> LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 4.dp),
            modifier = Modifier.fillMaxWidth().height(620.dp)
        ) {
            items(books, key = { it.id }) { book ->
                if (book.id == books.lastOrNull()?.id) LaunchedEffect(book.id) { onLoadMore() }
                ShelfCoverListItem(
                    book = book,
                    managementMode = managementMode && !book.isServerBook(),
                    selected = book.id in selectedIds,
                    onClick = { onBookClick(book) },
                    onSelect = { onSelect(book) }
                )
            }
        }

        else -> LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 4.dp),
            modifier = Modifier.fillMaxWidth().height(620.dp)
        ) {
            items(books, key = { it.id }) { book ->
                if (book.id == books.lastOrNull()?.id) LaunchedEffect(book.id) { onLoadMore() }
                ShelfCompactListItem(
                    book = book,
                    managementMode = managementMode && !book.isServerBook(),
                    selected = book.id in selectedIds,
                    onClick = { onBookClick(book) },
                    onSelect = { onSelect(book) }
                )
            }
        }
    }
}

@Composable
private fun ShelfCoverListItem(
    book: LocalBook,
    managementMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onSelect: () -> Unit
) {
    SoftCard(
        modifier = Modifier.clickable { if (managementMode) onSelect() else onClick() },
        contentPadding = 10.dp
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (managementMode) ShelfSelectionMark(selected)
            BookCover(book.title, width = 48.dp, height = 68.dp, imageUri = book.coverUri)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(book.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ShelfMetadataBadge("${shelfFormatLabel(book)}｜${if (book.isServerBook()) "远" else "本"}")
                    Text(book.author ?: "未知作者", color = DesignTokens.SoftText, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
                Text("阅读进度 ${(book.progress.percent * 100).toInt()}%", color = DesignTokens.SoftText, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ShelfCompactListItem(
    book: LocalBook,
    managementMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DesignTokens.CardBackground, RoundedCornerShape(8.dp))
            .clickable { if (managementMode) onSelect() else onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (managementMode) ShelfSelectionMark(selected)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(book.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                ShelfMetadataBadge("${shelfFormatLabel(book)}｜${if (book.isServerBook()) "远" else "本"}")
                Text(book.author ?: "未知作者", color = DesignTokens.SoftText, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("${(book.progress.percent * 100).toInt()}%", color = DesignTokens.SoftText, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ShelfMetadataBadge(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .border(1.dp, DesignTokens.Accent.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        color = DesignTokens.Accent,
        style = MaterialTheme.typography.labelSmall
    )
}

@Composable
private fun ShelfSelectionMark(selected: Boolean) {
    Icon(
        imageVector = Icons.Default.CheckCircle,
        contentDescription = "选择",
        tint = if (selected) DesignTokens.Accent else DesignTokens.SoftText,
        modifier = Modifier.size(24.dp)
    )
}

private fun shelfFormatLabel(book: LocalBook): String =
    book.format.displayName.firstOrNull()?.uppercaseChar()?.toString().orEmpty()

private fun LocalBook.isServerBook(): Boolean = id.startsWith("server:")

private fun LocalBook.serverBookId(): Long? =
    id.takeIf { isServerBook() }?.removePrefix("server:")?.toLongOrNull()

private fun LocalBook.isOpdsSource(): Boolean =
    source.equals("OPDS", ignoreCase = true) || uri.contains("/downloads/", ignoreCase = true)

private fun LocalBook.isLocalSource(): Boolean = !isServerBook() && !isOpdsSource()

@Composable
private fun ShelfFolderFilterRow(
    folders: List<ShelfFolder>,
    folderCounts: Map<String, Int>,
    selection: ShelfFolderSelection,
    favoriteCount: Int,
    unfiledCount: Int,
    totalCount: Int,
    onSelect: (ShelfFolderSelection) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ShelfFolderChip(
                label = "全部",
                count = totalCount,
                selected = selection == ShelfFolderSelection.All,
                onClick = { onSelect(ShelfFolderSelection.All) }
            )
        }
        item {
            ShelfFolderChip(
                label = "收藏",
                count = favoriteCount,
                selected = selection == ShelfFolderSelection.Favorites,
                onClick = { onSelect(ShelfFolderSelection.Favorites) }
            )
        }
        item {
            ShelfFolderChip(
                label = "未分组",
                count = unfiledCount,
                selected = selection == ShelfFolderSelection.Unfiled,
                onClick = { onSelect(ShelfFolderSelection.Unfiled) }
            )
        }
        folders.forEach { folder ->
            item(key = folder.id) {
                ShelfFolderChip(
                    label = folder.name,
                    count = folderCounts[folder.id] ?: 0,
                    selected = selection == ShelfFolderSelection.Folder(folder.id),
                    onClick = { onSelect(ShelfFolderSelection.Folder(folder.id)) }
                )
            }
        }
    }
}

@Composable
private fun ShelfFolderChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(DesignTokens.CardRadius)
            )
            .border(
                1.dp,
                if (selected) DesignTokens.Accent.copy(alpha = 0.35f) else DesignTokens.Hairline,
                RoundedCornerShape(DesignTokens.CardRadius)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .width(142.dp)
            .height(76.dp)
            .padding(DesignTokens.Space12),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space12),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(DesignTokens.Accent.copy(alpha = 0.12f), RoundedCornerShape(DesignTokens.RadiusMedium)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = DesignTokens.Accent)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text("$count 本", color = DesignTokens.SoftText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MoveToFolderDialog(
    folders: List<ShelfFolder>,
    selectedCount: Int,
    onDismiss: () -> Unit,
    onMoveToUnfiled: () -> Unit,
    onMoveToFolder: (String) -> Unit,
    onCreateFolder: (String) -> Unit
) {
    var newFolderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移动 $selectedCount 本书", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("选择目标文件夹，或新建文件夹后移动。", color = DesignTokens.SoftText)
                FolderTargetRow(
                    icon = Icons.Default.Folder,
                    title = "未分组",
                    subtitle = "从当前文件夹移出",
                    onClick = onMoveToUnfiled
                )
                folders.forEach { folder ->
                    FolderTargetRow(
                        icon = Icons.Default.Folder,
                        title = folder.name,
                        subtitle = "移动到此文件夹",
                        onClick = { onMoveToFolder(folder.id) }
                    )
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("新建文件夹名称") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = newFolderName.isNotBlank(),
                onClick = { onCreateFolder(newFolderName) }
            ) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                Text("新建并移动")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun FolderTargetRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8F4F0), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = DesignTokens.Accent)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, color = DesignTokens.SoftText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ShelfManagementBar(
    selectedCount: Int,
    allVisibleSelected: Boolean,
    favoriteSelected: Boolean,
    hasSelection: Boolean,
    onSelectAll: () -> Unit,
    onFavorite: () -> Unit,
    onMove: () -> Unit,
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
                    Checkbox(checked = allVisibleSelected, onCheckedChange = { onSelectAll() })
                    Text(if (allVisibleSelected) "取消全选" else "全选")
                }
                Text("已选 $selectedCount 本", color = DesignTokens.SoftText)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onFavorite,
                    enabled = hasSelection,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Accent),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp,
                        disabledElevation = 0.dp
                    )
                ) {
                    Icon(if (favoriteSelected) Icons.Default.FavoriteBorder else Icons.Default.Favorite, contentDescription = null)
                    Text(if (favoriteSelected) "取消收藏" else "收藏")
                }
                Button(
                    onClick = onMove,
                    enabled = hasSelection,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7A5C3A)),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp,
                        disabledElevation = 0.dp
                    )
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null)
                    Text("移动")
                }
                Button(
                    onClick = onRemove,
                    enabled = hasSelection,
                    modifier = Modifier.weight(1f),
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
                    Text("移出书架")
                }
            }
        }
    }
}

@Composable
private fun ImportLocalBookCard(
    message: String,
    isImporting: Boolean,
    onImportClick: () -> Unit
) {
    SoftCard(color = DesignTokens.WarmCard) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.AddBusiness, contentDescription = null, tint = DesignTokens.Accent)
            Column(Modifier.weight(1f)) {
                Text(
                    "导入本地书籍",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(message, color = DesignTokens.SoftText)
            }
            Button(
                enabled = !isImporting,
                onClick = onImportClick
            ) {
                if (isImporting) {
                    CircularProgressIndicator()
                } else {
                    Text("导入")
                }
            }
        }
    }
}

@Composable
private fun ContinueReadingHero(
    book: LocalBook,
    onReadClick: () -> Unit
) {
    SoftCard(
        modifier = Modifier.clickable(onClick = onReadClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space8)) {
            Text("◷", color = DesignTokens.Accent, style = MaterialTheme.typography.titleLarge)
            Text("继续阅读", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(DesignTokens.Space12))
        Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space16), verticalAlignment = Alignment.CenterVertically) {
            BookCover(
                title = book.title,
                width = 84.dp,
                height = 120.dp,
                imageUri = book.coverUri,
                placeholderTitleMaxLength = 8,
                placeholderMaxLines = 4
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DesignTokens.Space8)) {
                Text(book.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(book.author ?: "未知作者", color = DesignTokens.SoftText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space8)) {
                    WarmProgress(book.progress.percent, Modifier.weight(1f))
                    Text("${(book.progress.percent * 100).toInt()}%", color = DesignTokens.SoftText, style = MaterialTheme.typography.bodySmall)
                }
                Text(book.progress.chapterTitle ?: book.progress.positionLabel ?: "继续上次阅读", color = DesignTokens.SoftText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Button(
                    onClick = onReadClick,
                    colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Accent),
                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                    modifier = Modifier.align(Alignment.End)
                ) { Text("继续阅读") }
            }
        }
    }
}

@Composable
private fun ReadingBookCard(
    book: LocalBook,
    managementMode: Boolean,
    selected: Boolean,
    onCoverClick: () -> Unit,
    onReadClick: () -> Unit,
    onSelect: () -> Unit,
    onFavoriteClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = DesignTokens.SoftShadow,
                shape = RoundedCornerShape(DesignTokens.CardRadius),
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.08f)
            ),
        shape = RoundedCornerShape(DesignTokens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = DesignTokens.CardBackground)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                BookCover(
                    title = book.title,
                    modifier = Modifier.clickable(onClick = onCoverClick),
                    width = null,
                    height = 148.dp,
                    imageUri = book.coverUri,
                    brush = Brush.verticalGradient(listOf(Color(0xFF607D8B), Color(0xFF1B242A)))
                )
                if (managementMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(26.dp)
                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(999.dp))
                            .clickable(onClick = onSelect),
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
            }
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                Text(
                    book.author ?: "未知作者",
                    color = DesignTokens.SoftText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WarmProgress(book.progress.percent, Modifier.weight(1f))
                    Text("${(book.progress.percent * 100).toInt()}%", color = DesignTokens.SoftText, style = MaterialTheme.typography.labelSmall)
                }
                if (managementMode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onFavoriteClick, modifier = Modifier.size(34.dp)) {
                            Icon(
                                if (book.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "收藏",
                                tint = if (book.favorite) DesignTokens.Accent else DesignTokens.SoftText
                            )
                        }
                        IconButton(onClick = onRemoveClick, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.RemoveCircleOutline, contentDescription = "移出书架", tint = DesignTokens.SoftText)
                        }
                    }
                }
            }
        }
    }
}
