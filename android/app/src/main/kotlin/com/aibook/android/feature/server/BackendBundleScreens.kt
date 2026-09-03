package com.aibook.android.feature.server

import android.app.Application
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
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
import com.aibook.android.core.network.api.dto.ReadingProgressDTO
import com.aibook.android.di.ServiceLocator
import com.aibook.android.ui.design.BookCollectionScreen
import com.aibook.android.ui.design.BookCollectionState
import com.aibook.android.ui.design.BookCover
import com.aibook.android.ui.design.BookDetailTopBar
import com.aibook.android.ui.design.BookSource
import com.aibook.android.ui.design.BookSourceType
import com.aibook.android.ui.design.CollectionBook
import com.aibook.android.ui.design.DesignPage
import com.aibook.android.ui.design.DesignTokens
import com.aibook.android.ui.design.DetailActionButton
import com.aibook.android.ui.design.DetailInfoCard
import com.aibook.android.ui.design.DetailInfoItem
import com.aibook.android.ui.design.DetailIntroduction
import com.aibook.android.ui.design.DetailPrimaryButton
import com.aibook.android.ui.design.DetailTag
import com.aibook.android.ui.design.SoftCard
import com.aibook.android.ui.design.SlidingSegmentedControl
import com.aibook.android.ui.design.WarmProgress
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
    var showList by rememberSaveable { mutableStateOf(listMode) }
    var shelfFilter by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(section, listId) {
        viewModel.selectSection(section)
        if (listId != null) viewModel.selectBookList(listId)
    }
    val visibleBooks = state.books.filter { book ->
        when (shelfFilter) {
            1 -> book.id in state.shelfBookIds
            2 -> book.id !in state.shelfBookIds
            else -> true
        }
    }
    val collectionState = when {
        state.isLoading -> BookCollectionState.Loading
        state.errorMessage != null -> BookCollectionState.Error(state.errorMessage.orEmpty())
        visibleBooks.isEmpty() -> BookCollectionState.Empty(if (section == ServerLibrarySection.FAVORITES) "暂未收藏书籍" else "这里还没有书籍")
        else -> BookCollectionState.Content(visibleBooks.map { it.asCollectionBook(viewModel.coverUrl(it), it.id in state.shelfBookIds) })
    }
    val resolvedTitle = if (section == ServerLibrarySection.LISTS) {
        state.bookLists.firstOrNull { it.id == listId }?.name ?: title
    } else title
    DesignPage(
        title = resolvedTitle,
        modifier = Modifier.fillMaxSize(),
        navigation = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
        actions = {
            IconButton(onClick = {}) { Icon(Icons.Default.Search, "搜索") }
            IconButton(onClick = {}) { Icon(Icons.Default.FilterList, "筛选") }
        }
    ) {
        if (section == ServerLibrarySection.FAVORITES) {
            SlidingSegmentedControl(
                options = listOf("全部", "已加入书架", "未加入书架"),
                selectedIndex = shelfFilter,
                onSelected = { shelfFilter = it }
            )
            Spacer(Modifier.height(DesignTokens.Space12))
        } else {
            SlidingSegmentedControl(
                options = listOf("卡片", "列表"),
                selectedIndex = if (showList) 1 else 0,
                onSelected = { showList = it == 1 }
            )
            Spacer(Modifier.height(DesignTokens.Space12))
        }
        if (section == ServerLibrarySection.LISTS && collectionState is BookCollectionState.Content) {
            BackendBooklistContent(
                books = visibleBooks,
                coverUrl = viewModel::coverUrl,
                shelfIds = state.shelfBookIds,
                listMode = showList,
                onBookClick = { it.id?.let(onBookClick) },
                onToggleShelf = viewModel::toggleShelf
            )
        } else {
            BookCollectionScreen(
                state = collectionState,
                columns = columns,
                listMode = showList,
                onBookClick = { it.id.toLongOrNull()?.let(onBookClick) },
                onRetry = viewModel::refresh
            )
        }
    }
}

@Composable
private fun BackendBooklistContent(
    books: List<BookDTO>,
    coverUrl: (BookDTO) -> String?,
    shelfIds: Set<Long>,
    listMode: Boolean,
    onBookClick: (BookDTO) -> Unit,
    onToggleShelf: (BookDTO) -> Unit
) {
    if (listMode) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(DesignTokens.Space12)) {
            items(books, key = { it.id ?: it.title }) { book ->
                BackendBooklistCard(book, coverUrl(book), book.id in shelfIds, onBookClick, onToggleShelf)
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space12),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Space12)
        ) {
            gridItems(books, key = { it.id ?: it.title }) { book ->
                BackendBooklistCard(book, coverUrl(book), book.id in shelfIds, onBookClick, onToggleShelf)
            }
        }
    }
}

@Composable
private fun BackendBooklistCard(
    book: BookDTO,
    coverUrl: String?,
    onShelf: Boolean,
    onBookClick: (BookDTO) -> Unit,
    onToggleShelf: (BookDTO) -> Unit
) {
    SoftCard(contentPadding = DesignTokens.Space12, modifier = Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() }, indication = null
    ) { onBookClick(book) }) {
        BookCover(book.title, imageUri = coverUrl, width = null, height = 190.dp)
        Text(book.title, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(top = DesignTokens.Space8))
        Text(book.author ?: "未知作者", color = DesignTokens.SoftText, maxLines = 1)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Icon(Icons.Default.Star, null, tint = DesignTokens.Warning, modifier = Modifier.size(16.dp))
            Text(" ${book.rating ?: 0}.0", color = DesignTokens.SoftText, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            book.description?.ifBlank { "暂无简介" } ?: "暂无简介",
            color = DesignTokens.SoftText,
            maxLines = 2,
            style = MaterialTheme.typography.bodySmall
        )
        Button(
            onClick = { onToggleShelf(book) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (onShelf) MaterialTheme.colorScheme.surfaceVariant else DesignTokens.Accent,
                contentColor = if (onShelf) DesignTokens.Accent else androidx.compose.ui.graphics.Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp)
        ) { Text(if (onShelf) "已在书架" else "+ 加入书架") }
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
        navigation = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
        actions = {
            IconButton(onClick = onCreate) { Icon(Icons.Default.Add, "新建书单") }
            IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, "更多") }
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
                            BooklistMosaic(list.books)
                            Spacer(Modifier.width(DesignTokens.Space16))
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

@Composable
private fun BooklistMosaic(books: List<BookDTO>) {
    Column(Modifier.width(112.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        books.take(4).chunked(2).forEach { rowBooks ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                rowBooks.forEach { book -> BookCover(book.title, width = null, height = 70.dp, modifier = Modifier.weight(1f)) }
                repeat(2 - rowBooks.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (books.isEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(2) { BookCover("书单", width = null, height = 70.dp, modifier = Modifier.weight(1f)) }
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
    val progress: ReadingProgressDTO? = null,
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
            val progressResult = repository.getReadingProgress(id)
            bookResult.onSuccess { book ->
                val shelfIds = shelfResult.getOrNull()?.let { it.ungroupedBooks + it.groups.flatMap { group -> group.books } }
                    ?.mapNotNull { it.id }.orEmpty()
                _state.value = BackendBookDetailState(
                    loading = false,
                    book = book,
                    coverUrl = repository.resolveCoverUrl(book.coverUrl),
                    progress = progressResult.getOrNull(),
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
    val context = LocalContext.current
    var showMore by remember { mutableStateOf(false) }
    var localMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(bookId) { viewModel.load(bookId) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = DesignTokens.PagePadding),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Space16)
    ) {
        BookDetailTopBar(
            favorite = state.book?.isFavorite == true,
            onBack = onBack,
            onShare = {
                state.book?.let { book ->
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, listOf(book.title, book.author).filterNotNull().joinToString("\n"))
                    }, "分享书籍"))
                }
            },
            onFavorite = viewModel::toggleFavorite,
            onMore = { showMore = true }
        )
        DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
            DropdownMenuItem(text = { Text("刷新书籍信息") }, onClick = { showMore = false; viewModel.load(bookId) })
            DropdownMenuItem(text = { Text("后端书籍 ID：$bookId") }, onClick = { showMore = false })
        }
        when {
            state.loading -> BookCollectionScreen(BookCollectionState.Loading, onBookClick = {})
            state.error != null -> BookCollectionScreen(BookCollectionState.Error(state.error.orEmpty()), onBookClick = {}, onRetry = { viewModel.load(bookId) })
            state.book != null -> {
                val book = state.book!!
                val progress = state.progress?.totalProgress?.coerceIn(0, 100) ?: 0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space16),
                    verticalAlignment = Alignment.Top
                ) {
                    BookCover(book.title, width = 132.dp, height = 204.dp, imageUri = state.coverUrl)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DesignTokens.Space12)) {
                        Text(book.title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, maxLines = 3)
                        Text(book.author ?: "未知作者", style = MaterialTheme.typography.titleMedium)
                        DetailTag(book.categoryName ?: "我的书库")
                        Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space8)) {
                            book.tagNames.orEmpty().take(2).forEach { DetailTag(it) }
                        }
                        Text(
                            listOfNotNull(
                                "$progress%",
                                state.progress?.currentChapterTitle ?: book.chapterInfo
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        WarmProgress(progress / 100f, Modifier.fillMaxWidth())
                        DetailPrimaryButton(if (progress > 0) "继续阅读" else "开始阅读") { onRead(bookId) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space8)) {
                    DetailActionButton(Icons.AutoMirrored.Filled.MenuBook, if (state.onShelf) "已在书架" else "加入书架", state.onShelf, viewModel::toggleShelf)
                    DetailActionButton(
                        if (book.isFavorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        if (book.isFavorite == true) "已收藏" else "收藏",
                        book.isFavorite == true,
                        viewModel::toggleFavorite
                    )
                    DetailActionButton(Icons.Default.CloudDownload, "下载到本地") {
                        localMessage = "后端书籍可在线阅读，本地下载任务将在下载管理中提供"
                    }
                }
                Text(
                    "☁ 收藏与阅读进度将同步到后端服务",
                    modifier = Modifier.fillMaxWidth(),
                    color = DesignTokens.SoftText,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                (localMessage ?: state.message)?.let { Text(it, color = DesignTokens.Accent) }
                DetailIntroduction(book.description?.ifBlank { "暂无简介" } ?: "暂无简介")
                DetailInfoCard(
                    items = listOf(
                        DetailInfoItem("作者", book.author ?: "未知作者"),
                        DetailInfoItem("来源", book.categoryName ?: "我的书库"),
                        DetailInfoItem("字数 / 大小", book.fileSize?.let(::backendFileSizeLabel) ?: "未知"),
                        DetailInfoItem("格式", book.format ?: "未知"),
                        DetailInfoItem("最后阅读", state.progress?.lastReadAt ?: "尚未阅读")
                    )
                )
                Spacer(Modifier.height(DesignTokens.Space24))
            }
        }
    }
}

private fun backendFileSizeLabel(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / (1024f * 1024f))
    bytes >= 1024L -> String.format("%.1f KB", bytes / 1024f)
    else -> "$bytes B"
}
