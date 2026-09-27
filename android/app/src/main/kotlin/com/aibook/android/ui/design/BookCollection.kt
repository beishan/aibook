package com.aibook.android.ui.design

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect

enum class BookSourceType { LOCAL, OPDS, BACKEND }

data class BookSource(
    val type: BookSourceType,
    val sourceId: String? = null,
    val sourceName: String
) {
    companion object {
        val Local = BookSource(BookSourceType.LOCAL, sourceName = "本地")
    }
}

data class CollectionBook(
    val id: String,
    val title: String,
    val author: String = "未知作者",
    val coverUri: String? = null,
    val progress: Float? = null,
    val source: BookSource,
    val inShelf: Boolean = false,
    val favorite: Boolean = false
)

sealed interface BookCollectionState {
    data object Loading : BookCollectionState
    data class Empty(val message: String) : BookCollectionState
    data class Error(val message: String) : BookCollectionState
    data class Content(val books: List<CollectionBook>) : BookCollectionState
}

@Composable
fun BookCollectionScreen(
    state: BookCollectionState,
    columns: Int = 3,
    listMode: Boolean = false,
    onBookClick: (CollectionBook) -> Unit,
    onRetry: (() -> Unit)? = null,
    onLoadMore: (() -> Unit)? = null,
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false,
    loadMoreErrorMessage: String? = null,
    modifier: Modifier = Modifier
) {
    when (state) {
        BookCollectionState.Loading -> CollectionMessage(modifier) { CircularProgressIndicator(color = DesignTokens.Accent) }
        is BookCollectionState.Empty -> CollectionMessage(modifier) { Text(state.message, color = DesignTokens.SoftText) }
        is BookCollectionState.Error -> CollectionMessage(modifier) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DesignTokens.Space12)) {
                Text(state.message, color = DesignTokens.SoftText)
                onRetry?.let { retry ->
                    Button(
                        onClick = retry,
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp,
                            focusedElevation = 0.dp,
                            hoveredElevation = 0.dp,
                            disabledElevation = 0.dp
                        )
                    ) {
                        Text("重新加载")
                    }
                }
            }
        }
        is BookCollectionState.Content -> if (listMode) {
            BookList(
                books = state.books,
                onBookClick = onBookClick,
                modifier = modifier,
                onLoadMore = onLoadMore,
                hasMore = hasMore,
                isLoadingMore = isLoadingMore,
                loadMoreErrorMessage = loadMoreErrorMessage
            )
        } else {
            BookGrid(
                books = state.books,
                columns = columns,
                onBookClick = onBookClick,
                modifier = modifier,
                onLoadMore = onLoadMore,
                hasMore = hasMore,
                isLoadingMore = isLoadingMore,
                loadMoreErrorMessage = loadMoreErrorMessage
            )
        }
    }
}

@Composable
fun BookGrid(
    books: List<CollectionBook>,
    columns: Int = 3,
    onBookClick: (CollectionBook) -> Unit,
    onLoadMore: (() -> Unit)? = null,
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false,
    loadMoreErrorMessage: String? = null,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState, books.size, hasMore, isLoadingMore, loadMoreErrorMessage, onLoadMore) {
        if (
            onLoadMore == null || !hasMore || isLoadingMore || loadMoreErrorMessage != null
        ) {
            return@LaunchedEffect
        }
        snapshotFlow {
            gridState.layoutInfo.visibleItemsInfo
                .lastOrNull { it.index < books.size }
                ?.index ?: -1
        }.collect { lastVisibleBookIndex ->
            if (books.isNotEmpty() && lastVisibleBookIndex >= books.size - 6) {
                onLoadMore()
            }
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space12),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Space16)
    ) {
        items(books, key = { "${it.source.type}-${it.source.sourceId}-${it.id}" }) { book ->
            Column(
                modifier = Modifier.noPressEffect { onBookClick(book) },
                verticalArrangement = Arrangement.spacedBy(DesignTokens.Space8)
            ) {
                Box {
                    BookCover(book.title, width = null, height = 148.dp, imageUri = book.coverUri)
                    CoverSourceBadge(book.source.sourceName, Modifier.align(Alignment.TopEnd).padding(DesignTokens.Space8))
                }
                Text(book.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(book.author, color = DesignTokens.SoftText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                book.progress?.let { WarmProgress(it) }
            }
        }
        if (onLoadMore != null && (isLoadingMore || loadMoreErrorMessage != null)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LoadMoreFooter(isLoadingMore, loadMoreErrorMessage, onLoadMore)
            }
        }
    }
}

@Composable
fun BookList(
    books: List<CollectionBook>,
    onBookClick: (CollectionBook) -> Unit,
    onLoadMore: (() -> Unit)? = null,
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false,
    loadMoreErrorMessage: String? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState, books.size, hasMore, isLoadingMore, loadMoreErrorMessage, onLoadMore) {
        if (
            onLoadMore == null || !hasMore || isLoadingMore || loadMoreErrorMessage != null
        ) {
            return@LaunchedEffect
        }
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { lastVisibleBookIndex ->
                if (books.isNotEmpty() && lastVisibleBookIndex >= books.size - 6) {
                    onLoadMore()
                }
            }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Space12)
    ) {
        items(books, key = { "${it.source.type}-${it.source.sourceId}-${it.id}" }) { book ->
            BookListItem(book, onClick = { onBookClick(book) })
        }
        if (onLoadMore != null && (isLoadingMore || loadMoreErrorMessage != null)) {
            item { LoadMoreFooter(isLoadingMore, loadMoreErrorMessage, onLoadMore) }
        }
    }
}

@Composable
private fun LoadMoreFooter(
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = DesignTokens.Space12),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = DesignTokens.Accent,
                modifier = Modifier.padding(DesignTokens.Space8)
            )
        } else if (errorMessage != null) {
            TextButton(onClick = onRetry) {
                Text(
                    text = "加载失败，点击重试：$errorMessage"
                )
            }
        }
    }
}

@Composable
fun BookListItem(book: CollectionBook, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .noPressEffect(onClick)
            .padding(vertical = DesignTokens.Space4),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BookCover(book.title, width = 58.dp, height = 82.dp, imageUri = book.coverUri)
        Spacer(Modifier.width(DesignTokens.Space12))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DesignTokens.Space4)) {
            Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(book.author, color = DesignTokens.SoftText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            book.progress?.let { WarmProgress(it, Modifier.fillMaxWidth()) }
        }
        Spacer(Modifier.width(DesignTokens.Space8))
        SourceBadge(book.source.sourceName, book.source.type.name)
    }
}

@Composable
private fun CollectionMessage(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = { content() })
}

private fun Modifier.noPressEffect(onClick: () -> Unit): Modifier = clickable(
    interactionSource = MutableInteractionSource(),
    indication = null,
    onClick = onClick
)
