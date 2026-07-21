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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.aibook.android.ui.design.SectionHeader
import com.aibook.android.ui.design.SoftCard
import com.aibook.android.ui.design.WarmProgress

@Composable
fun ShelfScreen(
    onBookClick: (String) -> Unit,
    onReadClick: (String) -> Unit,
    viewModel: ShelfViewModel = viewModel(factory = ShelfViewModel.Factory),
    importViewModel: LocalBookImportViewModel = viewModel(factory = LocalBookImportViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val importState by importViewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLocalBookImportLauncher { uris ->
        importViewModel.importBooks(uris)
    }
    val visibleBooks = state.filteredBooks
    val featuredBooks = visibleBooks.take(3)
    val hasBooks = state.books.isNotEmpty()
    val allVisibleSelected = visibleBooks.isNotEmpty() && visibleBooks.all { it.id in state.selectedIds }
    val selectedFavorite = state.selectedBooks.isNotEmpty() && state.selectedBooks.all { it.favorite }
    var showMoveDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("shelf_prefs", android.content.Context.MODE_PRIVATE) }
    var viewMode by remember { mutableIntStateOf(prefs.getInt("reading_view_mode", 0)) }
    var showViewModeDialog by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(state.query.isNotBlank()) }

    DesignPage(
        title = if (state.managementMode) "已选 ${state.selectedIds.size} 本" else "",
        modifier = Modifier.fillMaxSize(),
        actions = {
            Icon(
                if (showSearch) Icons.Default.Close else Icons.Default.Search,
                contentDescription = if (showSearch) "关闭搜索" else "搜索书架",
                modifier = Modifier.clickable { showSearch = !showSearch }
            )
            Icon(
                imageVector = when (viewMode) {
                    0 -> Icons.Default.GridView
                    1 -> Icons.AutoMirrored.Filled.FormatListBulleted
                    else -> Icons.AutoMirrored.Filled.ViewList
                },
                contentDescription = "切换正在阅读视图",
                modifier = Modifier.clickable { showViewModeDialog = true }
            )
            Row(
                modifier = Modifier.clickable(onClick = viewModel::cycleSortOption),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "排序")
                Text(state.sortOption.label)
            }
            Text(
                if (state.managementMode) "取消" else "管理",
                modifier = Modifier.clickable { viewModel.setManagementMode(!state.managementMode) }
            )
        }
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp)) {
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
            item {
                Text(
                    text = "${visibleBooks.size} / ${state.books.size} 本书 · ${state.folders.size} 个文件夹 ›",
                    color = DesignTokens.SoftText,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (hasBooks) {
                item {
                    ShelfFolderFilterRow(
                        folders = state.folders,
                        folderCounts = state.folderCounts,
                        selection = state.folderSelection,
                        favoriteCount = state.books.count { it.favorite },
                        unfiledCount = state.books.count { it.folderId == null },
                        totalCount = state.books.size,
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
            if (featuredBooks.isNotEmpty() && !state.managementMode) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        featuredBooks.forEach { book ->
                            ContinueReadingCard(
                                book = book,
                                onReadClick = { onReadClick(book.id) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            if (visibleBooks.isNotEmpty()) {
                item { SectionHeader("正在阅读") }
                item {
                    ReadingBooksView(
                        books = visibleBooks,
                        viewMode = viewMode,
                        managementMode = state.managementMode,
                        selectedIds = state.selectedIds,
                        onBookClick = { book ->
                            if (state.managementMode) viewModel.toggleBookSelection(book.id) else onBookClick(book.id)
                        },
                        onReadClick = { onReadClick(it.id) },
                        onSelect = { viewModel.toggleBookSelection(it.id) },
                        onFavoriteClick = { viewModel.setFavorite(it.id, !it.favorite) },
                        onRemoveClick = { viewModel.toggleShelved(it.id, false) },
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

    if (showViewModeDialog) {
        ShelfViewModeDialog(
            currentMode = viewMode,
            onDismiss = { showViewModeDialog = false },
            onSelect = { mode ->
                viewMode = mode
                prefs.edit().putInt("reading_view_mode", mode).apply()
                showViewModeDialog = false
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
        0, 3 -> LazyVerticalGrid(
            columns = GridCells.Fixed(if (viewMode == 0) 3 else 4),
            contentPadding = PaddingValues(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(if (viewMode == 0) 12.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(if (viewMode == 0) 12.dp else 10.dp),
            modifier = Modifier.fillMaxWidth().height(620.dp)
        ) {
            gridItems(books, key = { it.id }) { book ->
                if (book.id == books.lastOrNull()?.id) LaunchedEffect(book.id) { onLoadMore() }
                ReadingBookCard(
                    book = book,
                    managementMode = managementMode,
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
                    managementMode = managementMode,
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
                    managementMode = managementMode,
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
                    ShelfMetadataBadge("${shelfFormatLabel(book)}｜本")
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
                ShelfMetadataBadge("${shelfFormatLabel(book)}｜本")
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

@Composable
private fun ShelfViewModeDialog(
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, contentDescription = null, tint = if (mode == currentMode) DesignTokens.Accent else DesignTokens.SoftText)
                        Text(label, fontWeight = if (mode == currentMode) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

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
                if (selected) DesignTokens.Accent.copy(alpha = 0.10f) else Color.White,
                RoundedCornerShape(18.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            tint = if (selected) DesignTokens.Accent else DesignTokens.SoftText,
            modifier = Modifier.size(18.dp)
        )
        Text(
            "$label $count",
            color = if (selected) DesignTokens.Accent else Color(0xFF2F2B26),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
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
private fun ContinueReadingCard(
    book: LocalBook,
    onReadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .shadow(
                elevation = DesignTokens.SoftShadow,
                shape = RoundedCornerShape(DesignTokens.CardRadius),
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.08f)
            )
            .clickable(onClick = onReadClick),
        shape = RoundedCornerShape(DesignTokens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BookCover(
                title = book.title,
                width = null,
                height = 84.dp,
                imageUri = book.coverUri,
                placeholderTitleMaxLength = Int.MAX_VALUE,
                placeholderMaxLines = 5,
                placeholderTextStyle = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 14.sp
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "阅读进度 ${(book.progress.percent * 100).toInt()}%",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            WarmProgress(book.progress.percent, Modifier.fillMaxWidth())
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
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 0.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
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
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}
