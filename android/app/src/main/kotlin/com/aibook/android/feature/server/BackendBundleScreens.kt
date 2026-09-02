package com.aibook.android.feature.server

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aibook.android.core.data.repository.ServerRepository
import com.aibook.android.core.network.api.dto.BookDTO
import com.aibook.android.di.ServiceLocator
import com.aibook.android.ui.design.BookCollectionScreen
import com.aibook.android.ui.design.BookCollectionState
import com.aibook.android.ui.design.BookCover
import com.aibook.android.ui.design.BookSource
import com.aibook.android.ui.design.BookSourceType
import com.aibook.android.ui.design.CollectionBook
import com.aibook.android.ui.design.DesignPage
import com.aibook.android.ui.design.DesignTokens
import com.aibook.android.ui.design.SoftCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Composable
fun BackendCollectionScreen(
    section: ServerLibrarySection,
    title: String,
    onBack: () -> Unit,
    onBookClick: (Long) -> Unit,
    listId: Long? = null,
    columns: Int = 3,
    listMode: Boolean = false,
    viewModel: ServerLibraryViewModel = viewModel(factory = ServerLibraryViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(section, listId) {
        viewModel.selectSection(section)
        if (listId != null) viewModel.selectBookList(listId)
    }
    val collectionState = when {
        state.isLoading -> BookCollectionState.Loading
        state.errorMessage != null -> BookCollectionState.Error(state.errorMessage.orEmpty())
        state.books.isEmpty() -> BookCollectionState.Empty(if (section == ServerLibrarySection.FAVORITES) "暂未收藏书籍" else "这里还没有书籍")
        else -> BookCollectionState.Content(state.books.map { it.asCollectionBook(viewModel.coverUrl(it), it.id in state.shelfBookIds) })
    }
    DesignPage(
        title = title,
        modifier = Modifier.fillMaxSize(),
        actions = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } }
    ) {
        BookCollectionScreen(
            state = collectionState,
            columns = columns,
            listMode = listMode,
            onBookClick = { it.id.toLongOrNull()?.let(onBookClick) },
            onRetry = viewModel::refresh
        )
    }
}

@Composable
fun BackendBooklistsScreen(
    onBack: () -> Unit,
    onBooklistClick: (Long) -> Unit,
    onCreate: () -> Unit,
    viewModel: ServerLibraryViewModel = viewModel(factory = ServerLibraryViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.selectSection(ServerLibrarySection.LISTS) }
    DesignPage(
        title = "书单",
        modifier = Modifier.fillMaxSize(),
        actions = {
            Text("新建", color = DesignTokens.Accent, modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCreate
            ))
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        }
    ) {
        when {
            state.isLoading -> BookCollectionScreen(BookCollectionState.Loading, onBookClick = {})
            state.errorMessage != null -> BookCollectionScreen(BookCollectionState.Error(state.errorMessage.orEmpty()), onBookClick = {}, onRetry = viewModel::refresh)
            state.bookLists.isEmpty() -> BookCollectionScreen(BookCollectionState.Empty("暂未创建书单"), onBookClick = {})
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(DesignTokens.Space12)) {
                items(state.bookLists, key = { it.id }) { list ->
                    SoftCard {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable(
                                interactionSource = remember { MutableInteractionSource() }, indication = null
                            ) { onBooklistClick(list.id) },
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DesignTokens.Space4)) {
                                Text(list.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(list.description?.ifBlank { "暂无描述" } ?: "暂无描述", color = DesignTokens.SoftText)
                                Text("${list.books.size} 本书", color = DesignTokens.Accent)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = DesignTokens.SoftText)
                        }
                    }
                }
            }
        }
    }
}

private fun BookDTO.asCollectionBook(cover: String?, onShelf: Boolean) = CollectionBook(
    id = id?.toString().orEmpty(),
    title = title,
    author = author ?: "未知作者",
    coverUri = cover,
    source = BookSource(BookSourceType.BACKEND, id?.toString(), "后端"),
    inShelf = onShelf,
    favorite = isFavorite == true
)

data class BackendBookDetailState(
    val loading: Boolean = true,
    val book: BookDTO? = null,
    val coverUrl: String? = null,
    val onShelf: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

class BackendBookDetailViewModel(private val repository: ServerRepository) : ViewModel() {
    private val _state = MutableStateFlow(BackendBookDetailState())
    val state: StateFlow<BackendBookDetailState> = _state.asStateFlow()

    fun load(id: Long) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val bookResult = repository.getBookById(id)
            val shelfResult = repository.getShelf()
            bookResult.onSuccess { book ->
                val shelfIds = shelfResult.getOrNull()?.let { it.ungroupedBooks + it.groups.flatMap { group -> group.books } }
                    ?.mapNotNull { it.id }.orEmpty()
                _state.value = BackendBookDetailState(
                    loading = false,
                    book = book,
                    coverUrl = repository.resolveCoverUrl(book.coverUrl),
                    onShelf = id in shelfIds
                )
            }.onFailure { _state.update { current -> current.copy(loading = false, error = it.message ?: "加载失败") } }
        }
    }

    fun toggleShelf() {
        val book = _state.value.book ?: return
        val id = book.id ?: return
        viewModelScope.launch {
            val removing = _state.value.onShelf
            val result = if (removing) repository.removeFromShelf(id) else repository.addToShelf(id)
            result.onSuccess { _state.update { it.copy(onShelf = !removing, message = if (removing) "已移出书架" else "已加入书架") } }
                .onFailure { error -> _state.update { it.copy(message = error.message ?: "操作失败") } }
        }
    }

    fun toggleFavorite() {
        val id = _state.value.book?.id ?: return
        viewModelScope.launch {
            repository.toggleFavorite(id).onSuccess {
                _state.update { state -> state.copy(book = state.book?.copy(isFavorite = state.book.isFavorite != true), message = "收藏状态已更新") }
            }.onFailure { error -> _state.update { it.copy(message = error.message ?: "操作失败") } }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                BackendBookDetailViewModel(ServiceLocator.get(app).serverRepository)
            }
        }
    }
}

@Composable
fun BackendBookDetailScreen(
    bookId: Long,
    onBack: () -> Unit,
    onRead: (Long) -> Unit,
    viewModel: BackendBookDetailViewModel = viewModel(factory = BackendBookDetailViewModel.Factory)
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(bookId) { viewModel.load(bookId) }
    DesignPage(
        title = "书籍详情",
        modifier = Modifier.fillMaxSize(),
        actions = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } }
    ) {
        when {
            state.loading -> BookCollectionScreen(BookCollectionState.Loading, onBookClick = {})
            state.error != null -> BookCollectionScreen(BookCollectionState.Error(state.error.orEmpty()), onBookClick = {}, onRetry = { viewModel.load(bookId) })
            state.book != null -> {
                val book = state.book!!
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.Space16)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space16)) {
                        BookCover(book.title, width = 118.dp, height = 168.dp, imageUri = state.coverUrl)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DesignTokens.Space8)) {
                            Text(book.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text(book.author ?: "未知作者", color = DesignTokens.SoftText)
                            Text(listOfNotNull(book.format, book.publisher, book.publishDate).joinToString(" · "), color = DesignTokens.SoftText)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space12)) {
                        Button(onClick = { onRead(bookId) }, modifier = Modifier.weight(1f)) { Text("开始阅读") }
                        Button(onClick = viewModel::toggleShelf, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.LibraryAdd, null)
                            Text(if (state.onShelf) "移出书架" else "加入书架")
                        }
                    }
                    Button(onClick = viewModel::toggleFavorite, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Favorite, null)
                        Text(if (book.isFavorite == true) "取消收藏" else "收藏")
                    }
                    state.message?.let { Text(it, color = DesignTokens.Accent) }
                    SoftCard {
                        Text("内容简介", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(DesignTokens.Space8))
                        Text(book.description?.ifBlank { "暂无简介" } ?: "暂无简介", color = DesignTokens.SoftText)
                    }
                    Spacer(Modifier.height(DesignTokens.Space24))
                }
            }
        }
    }
}
