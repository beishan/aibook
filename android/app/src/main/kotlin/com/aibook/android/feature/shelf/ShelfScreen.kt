package com.aibook.android.feature.shelf

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    val featured = visibleBooks.firstOrNull()
    val hasBooks = state.books.isNotEmpty()
    val allVisibleSelected = visibleBooks.isNotEmpty() && visibleBooks.all { it.id in state.selectedIds }
    val selectedFavorite = state.selectedBooks.isNotEmpty() && state.selectedBooks.all { it.favorite }
    var showMoveDialog by remember { mutableStateOf(false) }

    DesignPage(
        title = if (state.managementMode) "已选 ${state.selectedIds.size} 本" else "",
        modifier = Modifier.fillMaxSize(),
        actions = {
            Icon(Icons.Default.Search, contentDescription = "搜索")
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
            if (featured != null && !state.managementMode) {
                item {
                    ContinueReadingCard(
                        book = featured,
                        onCoverClick = { onBookClick(featured.id) },
                        onReadClick = { onReadClick(featured.id) }
                    )
                }
            }
            if (visibleBooks.isNotEmpty()) {
                item { SectionHeader("正在阅读") }
                item {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(620.dp)
                    ) {
                        items(visibleBooks, key = { it.id }) { book ->
                            ReadingBookCard(
                                book = book,
                                managementMode = state.managementMode,
                                selected = book.id in state.selectedIds,
                                onCoverClick = {
                                    if (state.managementMode) viewModel.toggleBookSelection(book.id)
                                    else onBookClick(book.id)
                                },
                                onReadClick = { onReadClick(book.id) },
                                onSelect = { viewModel.toggleBookSelection(book.id) },
                                onFavoriteClick = { viewModel.setFavorite(book.id, !book.favorite) },
                                onRemoveClick = { viewModel.toggleShelved(book.id, false) }
                            )
                        }
                    }
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
private fun ShelfFolderFilterRow(
    folders: List<ShelfFolder>,
    folderCounts: Map<String, Int>,
    selection: ShelfFolderSelection,
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
            .clickable(onClick = onClick)
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
    onCoverClick: () -> Unit,
    onReadClick: () -> Unit
) {
    val cardHeight = 188.dp
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
        colors = CardDefaults.cardColors(containerColor = DesignTokens.WarmCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
//                .height(cardHeight)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookCover(
                title = book.title,
                width = 106.dp,
                height = 160.dp,
                modifier = Modifier.clickable(onClick = onCoverClick)
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("上次阅读：昨天 22:15", color = DesignTokens.SoftText)
//                    SourceBadge("继续阅读")
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "更多",
                        tint = DesignTokens.SoftText
                    )
                }
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
//                Text(book.author ?: "未知作者 ›", color = DesignTokens.SoftText)
                Text("阅读进度 ${(book.progress.percent * 100).toInt()}%", color = DesignTokens.SoftText)
                WarmProgress(book.progress.percent, Modifier.fillMaxWidth())

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
//                    Text("上次阅读：昨天 22:15", color = DesignTokens.SoftText)
                    Button(onClick = onReadClick, modifier = Modifier.height(40.dp)) {
                        Text("继续阅读")
                    }
                }
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
    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                BookCover(
                    title = book.title,
                    modifier = Modifier.clickable(onClick = onCoverClick),
                    width = 72.dp,
                    height = 104.dp,
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
            Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
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
