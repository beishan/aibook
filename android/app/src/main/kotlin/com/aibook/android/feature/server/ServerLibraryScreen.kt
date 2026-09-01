package com.aibook.android.feature.server

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aibook.android.core.network.api.dto.BookDTO
import com.aibook.android.ui.design.BookCover
import com.aibook.android.ui.design.DesignPage
import com.aibook.android.ui.design.DesignTokens
import com.aibook.android.ui.design.SlidingSegmentedControl

@Composable
fun ServerLibraryScreen(
    onLocalLibraryClick: () -> Unit,
    onReadBook: (Long) -> Unit,
    initialSection: ServerLibrarySection = ServerLibrarySection.ALL,
    viewModel: ServerLibraryViewModel = viewModel(factory = ServerLibraryViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(initialSection) { viewModel.selectSection(initialSection) }

    DesignPage(
        title = if (initialSection == ServerLibrarySection.SHELF) "书架" else "发现",
        modifier = Modifier.fillMaxSize(),
        actions = {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "刷新服务端书库",
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = viewModel::refresh
                )
            )
        }
    ) {
        SlidingSegmentedControl(
            options = if (initialSection == ServerLibrarySection.SHELF) {
                listOf("本地书架", "服务端书架")
            } else {
                listOf("本地与 OPDS", "后端服务")
            },
            selectedIndex = 1,
            onSelected = { if (it == 0) onLocalLibraryClick() }
        )
        Spacer(Modifier.height(14.dp))
        SlidingSegmentedControl(
            options = ServerLibrarySection.entries.map { it.label },
            selectedIndex = state.section.ordinal,
            onSelected = { viewModel.selectSection(ServerLibrarySection.entries[it]) }
        )
        Spacer(Modifier.height(14.dp))

        if (state.section == ServerLibrarySection.LISTS && state.bookLists.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.bookLists, key = { it.id }) { list ->
                    val selected = list.id == state.selectedListId
                    Text(
                        text = list.name,
                        modifier = Modifier
                            .background(
                                if (selected) DesignTokens.Accent.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { viewModel.selectBookList(list.id) }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        color = if (selected) DesignTokens.Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        state.actionMessage?.let { message ->
            Text(
                text = message,
                color = DesignTokens.Accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = viewModel::clearMessage
                    )
                    .padding(vertical = 8.dp)
            )
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.errorMessage != null -> EmptyServerState(state.errorMessage.orEmpty())
            state.books.isEmpty() -> EmptyServerState(
                if (state.section == ServerLibrarySection.LISTS && state.bookLists.isEmpty()) "暂无书单"
                else "这里还没有书籍"
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.books, key = { it.id ?: "${it.title}-${it.author}" }) { book ->
                    ServerBookCard(
                        book = book,
                        coverUrl = viewModel.coverUrl(book),
                        onRead = { book.id?.let(onReadBook) },
                        onToggleShelf = { viewModel.toggleShelf(book) },
                        onToggleFavorite = { viewModel.toggleFavorite(book) },
                        isOnShelf = book.id in state.shelfBookIds
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerBookCard(
    book: BookDTO,
    coverUrl: String?,
    isOnShelf: Boolean,
    onRead: () -> Unit,
    onToggleShelf: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BookCover(title = book.title, imageUri = coverUrl, width = 62.dp, height = 88.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(book.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                book.author ?: "未知作者",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ServerAction("阅读", onRead)
                ServerAction(if (isOnShelf) "移出书架" else "加入书架", onToggleShelf)
                ServerAction(if (book.isFavorite == true) "取消收藏" else "收藏", onToggleFavorite)
            }
        }
    }
}

@Composable
private fun ServerAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = DesignTokens.Accent,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 6.dp)
    )
}

@Composable
private fun EmptyServerState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
