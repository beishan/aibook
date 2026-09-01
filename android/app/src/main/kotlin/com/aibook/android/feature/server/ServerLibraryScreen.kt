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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aibook.android.core.network.api.dto.BookDTO
import com.aibook.android.core.network.api.dto.BookListDTO
import com.aibook.android.ui.design.BookCover
import com.aibook.android.ui.design.DesignPage
import com.aibook.android.ui.design.DesignTokens
import com.aibook.android.ui.design.SlidingSegmentedControl

@Composable
fun ServerLibraryScreen(
    onLocalLibraryClick: () -> Unit,
    onOpdsClick: () -> Unit = {},
    onReadBook: (Long) -> Unit,
    initialSection: ServerLibrarySection = ServerLibrarySection.ALL,
    overviewMode: Boolean = true,
    onSectionClick: (ServerLibrarySection) -> Unit = {},
    onBack: (() -> Unit)? = null,
    viewModel: ServerLibraryViewModel = viewModel(factory = ServerLibraryViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(overviewMode, initialSection) {
        if (overviewMode) viewModel.loadOverview() else viewModel.selectSection(initialSection)
    }

    DesignPage(
        title = when {
            initialSection == ServerLibrarySection.SHELF -> "书架"
            !overviewMode -> initialSection.label
            else -> "书城"
        },
        modifier = Modifier.fillMaxSize(),
        actions = {
            onBack?.let { back ->
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.noRippleClick(back)
                )
            }
            Icon(
                Icons.Default.Refresh,
                contentDescription = "刷新服务端书库",
                modifier = Modifier.noRippleClick {
                    if (overviewMode) viewModel.loadOverview() else viewModel.refresh()
                }
            )
        }
    ) {
        if (onBack == null) {
            SlidingSegmentedControl(
                options = if (initialSection == ServerLibrarySection.SHELF) {
                    listOf("本地书架", "服务端书架")
                } else {
                    listOf("本地", "OPDS", "远程")
                },
                selectedIndex = if (initialSection == ServerLibrarySection.SHELF) 1 else 2,
                onSelected = { index ->
                    if (initialSection == ServerLibrarySection.SHELF) {
                        if (index == 0) onLocalLibraryClick()
                    } else {
                        when (index) {
                            0 -> onLocalLibraryClick()
                            1 -> onOpdsClick()
                        }
                    }
                }
            )
            Spacer(Modifier.height(14.dp))
        }

        state.actionMessage?.let { message ->
            Text(
                text = message,
                color = DesignTokens.Accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .noRippleClick(viewModel::clearMessage)
                    .padding(vertical = 8.dp)
            )
        }

        when {
            state.isLoading -> LoadingState()
            state.errorMessage != null -> EmptyServerState(state.errorMessage.orEmpty())
            overviewMode -> RemoteOverview(
                books = state.overviewBooks,
                favorites = state.favoritePreview,
                bookLists = state.bookLists,
                coverUrl = viewModel::coverUrl,
                onSectionClick = onSectionClick
            )
            else -> ServerSectionContent(
                state = state,
                coverUrl = viewModel::coverUrl,
                onSelectBookList = viewModel::selectBookList,
                onReadBook = onReadBook,
                onToggleShelf = viewModel::toggleShelf,
                onToggleFavorite = viewModel::toggleFavorite
            )
        }
    }
}

@Composable
private fun RemoteOverview(
    books: List<BookDTO>,
    favorites: List<BookDTO>,
    bookLists: List<BookListDTO>,
    coverUrl: (BookDTO) -> String?,
    onSectionClick: (ServerLibrarySection) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            RemoteBookPreviewCard(
                title = "书籍列表",
                emptyText = "服务端暂无书籍",
                books = books,
                coverUrl = coverUrl,
                onClick = { onSectionClick(ServerLibrarySection.ALL) }
            )
        }
        item {
            RemoteBookPreviewCard(
                title = "收藏",
                emptyText = "暂未收藏书籍",
                books = favorites,
                coverUrl = coverUrl,
                onClick = { onSectionClick(ServerLibrarySection.FAVORITES) }
            )
        }
        item {
            RemoteListPreviewCard(
                lists = bookLists,
                onClick = { onSectionClick(ServerLibrarySection.LISTS) }
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun RemoteBookPreviewCard(
    title: String,
    emptyText: String,
    books: List<BookDTO>,
    coverUrl: (BookDTO) -> String?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .noRippleClick(onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PreviewHeader(title)
        if (books.isEmpty()) {
            Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            books.take(4).forEach { book ->
                PreviewBookRow(book, coverUrl(book))
            }
        }
    }
}

@Composable
private fun RemoteListPreviewCard(lists: List<BookListDTO>, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .noRippleClick(onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PreviewHeader("书单")
        if (lists.isEmpty()) {
            Text("暂未创建书单", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            lists.take(4).forEach { list ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(list.name, fontWeight = FontWeight.SemiBold)
                        list.description?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text("${list.books.size} 本", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PreviewHeader(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("查看全部", color = DesignTokens.Accent, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PreviewBookRow(book: BookDTO, coverUrl: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BookCover(title = book.title, imageUri = coverUrl, width = 38.dp, height = 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(book.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                book.author ?: "未知作者",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ServerSectionContent(
    state: ServerLibraryUiState,
    coverUrl: (BookDTO) -> String?,
    onSelectBookList: (Long) -> Unit,
    onReadBook: (Long) -> Unit,
    onToggleShelf: (BookDTO) -> Unit,
    onToggleFavorite: (BookDTO) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
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
                            .noRippleClick { onSelectBookList(list.id) }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        color = if (selected) DesignTokens.Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (state.books.isEmpty()) {
            EmptyServerState(if (state.section == ServerLibrarySection.LISTS) "暂无书单内容" else "这里还没有书籍")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.books, key = { it.id ?: "${it.title}-${it.author}" }) { book ->
                    ServerBookCard(
                        book = book,
                        coverUrl = coverUrl(book),
                        onRead = { book.id?.let(onReadBook) },
                        onToggleShelf = { onToggleShelf(book) },
                        onToggleFavorite = { onToggleFavorite(book) },
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
            .noRippleClick(onClick)
            .padding(vertical = 6.dp)
    )
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyServerState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun Modifier.noRippleClick(onClick: () -> Unit): Modifier = clickable(
    interactionSource = MutableInteractionSource(),
    indication = null,
    onClick = onClick
)
