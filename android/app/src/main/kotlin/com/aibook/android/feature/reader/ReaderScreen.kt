package com.aibook.android.feature.reader

import android.graphics.Typeface
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aibook.android.core.model.AccentColor
import com.aibook.android.core.model.AppThemeMode
import com.aibook.android.core.model.BookFormat
import com.aibook.android.core.model.PageTurnMode
import com.aibook.android.core.model.ParagraphSpacing
import com.aibook.android.core.model.ReaderContentsStyle
import com.aibook.android.core.model.ReaderAutoScrollSpeed
import com.aibook.android.core.model.ReaderFontCatalog
import com.aibook.android.core.model.ReaderFontType
import com.aibook.android.core.model.ReaderSettings
import com.aibook.android.core.model.ReaderTheme
import com.aibook.android.core.model.ReaderOrientationMode
import com.aibook.android.core.model.TextAlignment
import com.aibook.android.core.model.usesPagedReading
import com.aibook.android.feature.settings.SettingsViewModel
import com.aibook.android.core.reader.ReaderChapter
import com.aibook.android.core.reader.ReaderBookmark
import com.aibook.android.core.reader.ReaderHighlight
import com.aibook.android.ui.design.BookCover
import com.aibook.android.ui.design.DesignTokens
import com.aibook.android.ui.design.WarmProgress
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

private enum class ReaderPanel {
    None,
    Contents,
    Bookmarks,
    Highlights,
    Settings,
    Theme
}

private data class ReaderVisiblePosition(
    val chapterIndex: Int,
    val lineIndex: Int,
    val scrollOffset: Int
)

private data class HighlightDraft(val chapter: ReaderChapter, val lineIndex: Int, val text: String)

private data class ReaderPageContent(
    val chapterIndex: Int,
    val startLineIndex: Int,
    val endLineIndex: Int,
    val title: String?,
    val paragraphs: List<String>,
    val imageUri: String? = null
)

@Composable
fun ReaderScreen(
    bookId: String,
    isRemote: Boolean,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = viewModel(factory = ReaderViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = state.settings
    var panel by remember { mutableStateOf(ReaderPanel.None) }
    var touchLocked by remember(bookId, isRemote) { mutableStateOf(false) }
    var autoPlaying by remember(bookId, isRemote) { mutableStateOf(false) }
    // 提升 scrollState 到此处，避免打开目录/设置后返回时丢失滚动位置
    val scrollState = rememberLazyListState()
    val lifecycleOwner = LocalLifecycleOwner.current

    ReaderWindowEffects(settings = settings, autoPlaying = autoPlaying)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) autoPlaying = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(panel) {
        if (panel != ReaderPanel.None) autoPlaying = false
    }

    LaunchedEffect(bookId, isRemote) {
        if (isRemote) {
            viewModel.loadRemoteBook(bookId.toLongOrNull() ?: 0L)
        } else {
            viewModel.loadLocalBook(bookId)
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.saveProgress() }
    }

    BackHandler {
        when (panel) {
            ReaderPanel.None -> viewModel.saveProgressThen(onBack)
            else -> panel = ReaderPanel.None
        }
    }

    when (panel) {
        ReaderPanel.Contents -> ReaderContentsPage(
            state = state,
            onBack = { panel = ReaderPanel.None },
            onOpenBookmarks = { panel = ReaderPanel.Bookmarks },
            onChapterClick = {
                viewModel.selectChapter(it)
                panel = ReaderPanel.None
            }
        )
        ReaderPanel.Bookmarks -> ReaderBookmarksPage(
            bookmarks = state.bookmarks,
            onBack = { panel = ReaderPanel.Contents },
            onBookmarkClick = {
                viewModel.openBookmark(it)
                panel = ReaderPanel.None
            },
            onDelete = viewModel::removeBookmark
        )
        ReaderPanel.Highlights -> ReaderHighlightsPage(
            highlights = state.highlights,
            onBack = { panel = ReaderPanel.None },
            onDelete = viewModel::removeHighlight
        )
        ReaderPanel.Settings -> ReadingSettingsPage(
            state = state,
            viewModel = viewModel,
            autoPlaying = autoPlaying,
            onStartAutoPlay = {
                viewModel.confirmSettings()
                autoPlaying = true
                panel = ReaderPanel.None
            },
            onLockTouch = {
                viewModel.confirmSettings()
                touchLocked = true
                panel = ReaderPanel.None
            },
            onBack = {
                viewModel.confirmSettings()
                panel = ReaderPanel.None
            }
        )
        ReaderPanel.Theme -> ReaderThemePage(
            settings = settings,
            onBack = { panel = ReaderPanel.None },
            onThemeChange = viewModel::setTheme
        )
        ReaderPanel.None -> ReaderMainPage(
            state = state,
            settings = settings,
            scrollState = scrollState,
            onBack = { viewModel.saveProgressThen(onBack) },
            onOpenContents = { panel = ReaderPanel.Contents },
            onOpenSettings = { panel = ReaderPanel.Settings },
            onOpenTheme = { panel = ReaderPanel.Theme },
            onProgressChange = viewModel::updateScrollProgress,
            onLoadNextChapter = viewModel::appendNextChapter,
            onLoadPreviousChapter = viewModel::prependPreviousChapter,
            onToggleFavorite = viewModel::toggleFavorite,
            onToggleBookmark = viewModel::toggleBookmark,
            onAddHighlight = viewModel::addHighlight,
            onOpenHighlights = { panel = ReaderPanel.Highlights },
            onOpenSearchMatch = viewModel::openSearchMatch,
            onToggleQuickTheme = {
                viewModel.setTheme(if (settings.theme == ReaderTheme.DARK) ReaderTheme.LIGHT else ReaderTheme.DARK)
            },
            onSelectChapter = viewModel::selectChapter,
            onReadingPositionChanged = viewModel::updateReadingPosition,
            touchLocked = touchLocked,
            autoPlaying = autoPlaying,
            onUnlockTouch = { touchLocked = false },
            onAutoPlayingChange = { autoPlaying = it }
        )
    }
}

@Composable
fun ReaderThemeSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ThemeSettingsPage(
        appThemeMode = state.appThemeMode,
        accentColor = state.accentColor,
        readerTheme = state.readerTheme,
        onBack = onBack,
        onAppThemeModeChange = viewModel::setAppThemeMode,
        onAccentColorChange = viewModel::setAccentColor,
        onReaderThemeChange = viewModel::setReaderTheme
    )
}

@Composable
private fun ReaderMainPage(
    state: ReaderUiState,
    settings: ReaderSettings,
    scrollState: androidx.compose.foundation.lazy.LazyListState,
    onBack: () -> Unit,
    onOpenContents: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTheme: () -> Unit,
    onProgressChange: (Float) -> Unit,
    onLoadNextChapter: () -> Unit,
    onLoadPreviousChapter: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleBookmark: () -> Unit,
    onAddHighlight: (ReaderChapter, Int, String, String?, Long) -> Unit,
    onOpenHighlights: () -> Unit,
    onOpenSearchMatch: (ReaderSearchMatch) -> Unit,
    onToggleQuickTheme: () -> Unit,
    onSelectChapter: (Int) -> Unit,
    onReadingPositionChanged: (Int, Int, Int) -> Unit,
    touchLocked: Boolean,
    autoPlaying: Boolean,
    onUnlockTouch: () -> Unit,
    onAutoPlayingChange: (Boolean) -> Unit
) {
    val colors = readerColors(settings.theme)
    var controlsVisible by remember(state.book?.id) { mutableStateOf(false) }
    var searchVisible by remember(state.book?.id) { mutableStateOf(false) }
    var searchQuery by remember(state.book?.id) { mutableStateOf("") }
    var searchMatchIndex by remember(state.book?.id) { mutableStateOf(-1) }
    var highlightDraft by remember { mutableStateOf<HighlightDraft?>(null) }
    val searchMatches = remember(state.chapters, searchQuery) {
        ReaderSearchCatalog.find(state.chapters, searchQuery)
    }

    var restoredInitialPosition by remember(state.book?.id, state.remoteBookId) { mutableStateOf(false) }

    // 章节切换时滚动到顶部；初次打开时恢复到保存的章节内行号
    LaunchedEffect(state.loadedChapters.firstOrNull()?.index, state.book?.id, state.remoteBookId, settings.pageTurnMode) {
        if (state.loadedChapters.size == 1 && !settings.pageTurnMode.usesPagedReading()) {
            val savedProgress = state.book?.progress
            val loadedChapter = state.loadedChapters.firstOrNull()
            val canRestore = !restoredInitialPosition &&
                savedProgress != null &&
                loadedChapter != null &&
                (savedProgress.chapterIndex == loadedChapter.index || savedProgress.chapterHref == loadedChapter.href)
            val targetItem = if (canRestore && savedProgress?.lineIndex != null) {
                (savedProgress.lineIndex ?: 0) + 1
            } else {
                0
            }
            val targetOffset = if (canRestore) savedProgress?.scrollOffset ?: 0 else 0
            scrollState.scrollToItem(targetItem.coerceAtLeast(0), targetOffset.coerceAtLeast(0))
            restoredInitialPosition = true
        }
    }

    LaunchedEffect(state.bookmarkNavigation?.requestId) {
        val request = state.bookmarkNavigation ?: return@LaunchedEffect
        if (!settings.pageTurnMode.usesPagedReading()) {
            scrollState.scrollToItem((request.lineIndex + 1).coerceAtLeast(0), request.scrollOffset.coerceAtLeast(0))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(touchLocked, autoPlaying) {
                    if (!touchLocked) {
                        detectTapGestures {
                            if (autoPlaying) onAutoPlayingChange(false)
                            else controlsVisible = !controlsVisible
                        }
                    }
                }
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = DesignTokens.Accent
                )
                state.errorMessage != null -> Text(
                    text = state.errorMessage,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(28.dp),
                    color = colors.foreground,
                    textAlign = TextAlign.Center
                )
                state.hasReadableContent -> ReaderTextContent(
                    state = state,
                    settings = settings,
                    scrollState = scrollState,
                    foreground = colors.foreground,
                    onLoadNextChapter = onLoadNextChapter,
                    onLoadPreviousChapter = onLoadPreviousChapter,
                    onReadingPositionChanged = onReadingPositionChanged,
                    touchLocked = touchLocked,
                    autoPlaying = autoPlaying,
                    onAutoPlayStopped = { onAutoPlayingChange(false) },
                    onParagraphLongPress = { chapter, lineIndex, text ->
                        onAutoPlayingChange(false)
                        highlightDraft = HighlightDraft(chapter, lineIndex, text)
                    },
                    onParagraphTap = {
                        if (autoPlaying) onAutoPlayingChange(false)
                        else controlsVisible = !controlsVisible
                    }
                )
                else -> Text(
                    text = "暂无可阅读内容",
                    modifier = Modifier.align(Alignment.Center),
                    color = colors.muted
                )
            }
        }
        highlightDraft?.let { draft ->
            var note by remember(draft) { mutableStateOf("") }
            var color by remember(draft) { mutableStateOf(0xFFFFE082L) }
            AlertDialog(
                onDismissRequest = { highlightDraft = null },
                title = { Text("添加高亮") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(draft.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf(0xFFFFE082L, 0xFFB9E6A7L, 0xFF90CAF9L).forEach { option ->
                                Box(Modifier.size(28.dp).clip(CircleShape).background(Color(option)).clickable { color = option })
                            }
                        }
                        OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("批注（可选）") })
                    }
                },
                confirmButton = { TextButton(onClick = { onAddHighlight(draft.chapter, draft.lineIndex, draft.text, note.ifBlank { null }, color); highlightDraft = null }) { Text("保存") } },
                dismissButton = { TextButton(onClick = { highlightDraft = null }) { Text("取消") } }
            )
        }
        if (controlsVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .background(colors.background)
                    .statusBarsPadding()
                    .fillMaxWidth()
            ) {
                ReaderTopBar(
                    title = state.book?.title ?: "在线书籍",
                    chapterTitle = state.currentChapterTitle,
                    colors = colors,
                    isBookmarked = state.isCurrentPositionBookmarked,
                    onBack = onBack,
                    onOpenSearch = {
                        onAutoPlayingChange(false)
                        searchVisible = true
                    },
                    onToggleBookmark = onToggleBookmark,
                    onOpenHighlights = {
                        onAutoPlayingChange(false)
                        onOpenHighlights()
                    }
                )
                if (searchVisible) {
                    ReaderSearchBar(
                        query = searchQuery,
                        matchCount = searchMatches.size,
                        matchIndex = searchMatchIndex,
                        colors = colors,
                        onQueryChange = {
                            searchQuery = it
                            searchMatchIndex = -1
                        },
                        onPrevious = {
                            searchMatchIndex = ReaderSearchCatalog.nextIndex(
                                currentIndex = searchMatchIndex,
                                count = searchMatches.size,
                                forward = false
                            )
                            searchMatches.getOrNull(searchMatchIndex)?.let(onOpenSearchMatch)
                        },
                        onNext = {
                            searchMatchIndex = ReaderSearchCatalog.nextIndex(
                                currentIndex = searchMatchIndex,
                                count = searchMatches.size,
                                forward = true
                            )
                            searchMatches.getOrNull(searchMatchIndex)?.let(onOpenSearchMatch)
                        },
                        onClose = { searchVisible = false }
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                ReaderBottomBar(
                    progress = state.scrollProgress,
                    currentChapterIndex = state.currentChapterIndex,
                    totalChapters = state.chapters.size,
                    colors = colors,
                    onProgressChange = {
                        onAutoPlayingChange(false)
                        onProgressChange(it)
                    },
                    onOpenContents = onOpenContents,
                    onOpenSettings = onOpenSettings,
                    onOpenTheme = onOpenTheme,
                    isFavorite = state.book?.favorite ?: false,
                    onToggleFavorite = onToggleFavorite,
                    quickThemeLabel = if (settings.theme == ReaderTheme.DARK) "亮色" else "暗色",
                    onToggleQuickTheme = {
                        onAutoPlayingChange(false)
                        onToggleQuickTheme()
                    },
                    onSelectChapter = {
                        onAutoPlayingChange(false)
                        onSelectChapter(it)
                    }
                )
            }
        }
        if (autoPlaying) {
            ReaderAutoPlayOverlay(
                modifier = Modifier.align(Alignment.BottomCenter),
                settings = settings,
                colors = colors,
                onPause = { onAutoPlayingChange(false) }
            )
        }
        if (touchLocked) {
            ReaderTouchLockOverlay(colors = colors, onUnlock = onUnlockTouch)
        }
    }
}

@Composable
private fun ReaderAutoPlayOverlay(
    modifier: Modifier = Modifier,
    settings: ReaderSettings,
    colors: ReaderPalette,
    onPause: () -> Unit
) {
    val speedLabel = if (settings.pageTurnMode.usesPagedReading()) {
        "${settings.autoPageIntervalSeconds} 秒/页"
    } else {
        when (settings.autoScrollSpeed) {
            ReaderAutoScrollSpeed.SLOW -> "慢速"
            ReaderAutoScrollSpeed.MEDIUM -> "中速"
            ReaderAutoScrollSpeed.FAST -> "快速"
        }
    }
    Row(
        modifier = modifier
            .padding(bottom = 28.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(colors.foreground.copy(alpha = 0.88f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onPause
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.Pause, contentDescription = "暂停自动阅读", tint = colors.background)
        Text("自动阅读 · $speedLabel", color = colors.background, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ReaderTouchLockOverlay(
    colors: ReaderPalette,
    onUnlock: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val released = withTimeoutOrNull(2_000L) { waitForUpOrCancellation() }
                    if (released == null) onUnlock()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(colors.foreground.copy(alpha = 0.78f))
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = colors.background)
            Text("已锁定触摸", color = colors.background, fontWeight = FontWeight.Bold)
            Text("长按 2 秒解锁", color = colors.background.copy(alpha = 0.82f))
        }
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    chapterTitle: String,
    colors: ReaderPalette,
    isBookmarked: Boolean,
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenHighlights: () -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = 8.dp, vertical = if (isLandscape) 2.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = colors.foreground)
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(title, color = colors.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                chapterTitle.ifBlank { "正在阅读" },
                color = colors.muted,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onOpenSearch) {
            Icon(Icons.Default.Search, contentDescription = "搜索书内内容", tint = colors.foreground)
        }
        IconButton(onClick = onToggleBookmark) {
            Icon(
                if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                contentDescription = if (isBookmarked) "取消书签" else "添加书签",
                tint = if (isBookmarked) DesignTokens.Accent else colors.foreground
            )
        }
        IconButton(onClick = onOpenHighlights) {
            Icon(Icons.Default.MoreVert, contentDescription = "全部笔记", tint = colors.foreground)
        }
    }
}

@Composable
private fun ReaderSearchBar(
    query: String,
    matchCount: Int,
    matchIndex: Int,
    colors: ReaderPalette,
    onQueryChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .border(1.dp, colors.divider)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("搜索书内内容") }
        )
        Text(
            text = if (query.isBlank()) "" else "${if (matchIndex !in 0 until matchCount) 0 else matchIndex + 1}/$matchCount",
            color = colors.muted,
            modifier = Modifier.padding(start = 8.dp)
        )
        IconButton(onClick = onPrevious, enabled = matchCount > 0) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上一处", tint = colors.foreground)
        }
        IconButton(onClick = onNext, enabled = matchCount > 0) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下一处", tint = colors.foreground)
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "关闭搜索", tint = colors.foreground)
        }
    }
}

@Composable
private fun ReaderTextContent(
    state: ReaderUiState,
    settings: ReaderSettings,
    scrollState: androidx.compose.foundation.lazy.LazyListState,
    foreground: Color,
    onLoadNextChapter: () -> Unit,
    onLoadPreviousChapter: () -> Unit,
    onReadingPositionChanged: (Int, Int, Int) -> Unit,
    touchLocked: Boolean,
    autoPlaying: Boolean,
    onAutoPlayStopped: () -> Unit,
    onParagraphLongPress: (ReaderChapter, Int, String) -> Unit,
    onParagraphTap: () -> Unit
) {
    val loadedChapters = state.loadedChapters
    val chapterParagraphs = remember(loadedChapters, state.book?.format, settings.compressTxtBlankLines, settings.mergeTxtShortLines) {
        loadedChapters.map { chapter ->
            val paragraphs = if (state.book?.format == BookFormat.TXT) {
                TxtParagraphNormalizer.normalize(chapter.content, settings.compressTxtBlankLines).let { if (settings.mergeTxtShortLines) TxtParagraphNormalizer.mergeHardWrappedLines(it) else it }
            } else {
                chapter.content.split("\n").filter { it.isNotBlank() }
            }
            chapter to paragraphs
        }
    }
    val chapterItemCounts = remember(chapterParagraphs) {
        chapterParagraphs.map { (chapter, paragraphs) -> chapter.index to 1 + paragraphs.size }
    }
    val fontFamily = rememberReaderFontFamily(settings)

    if (settings.pageTurnMode.usesPagedReading()) {
        ReaderPagedContent(
            state = state,
            settings = settings,
            foreground = foreground,
            chapterParagraphs = chapterParagraphs,
            onLoadNextChapter = onLoadNextChapter,
            onLoadPreviousChapter = onLoadPreviousChapter,
            onReadingPositionChanged = onReadingPositionChanged,
            touchLocked = touchLocked,
            autoPlaying = autoPlaying,
            onAutoPlayStopped = onAutoPlayStopped
        )
        return
    }

    // 监听滚动到底部，自动加载下一章
    val shouldLoadMore = remember(scrollState, loadedChapters) {
        derivedStateOf {
            val lastVisible = scrollState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = scrollState.layoutInfo.totalItemsCount
            totalItems > 0 && lastVisible >= totalItems - 3
        }
    }
    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) onLoadNextChapter()
    }

    val shouldLoadPrevious = remember(scrollState, loadedChapters) {
        derivedStateOf {
            ReaderChapterWindow.shouldPrependPrevious(
                firstVisibleItemIndex = scrollState.firstVisibleItemIndex,
                scrollOffset = scrollState.firstVisibleItemScrollOffset
            )
        }
    }
    LaunchedEffect(shouldLoadPrevious.value) {
        if (shouldLoadPrevious.value) onLoadPreviousChapter()
    }

    val density = LocalDensity.current.density
    LaunchedEffect(autoPlaying, settings.autoScrollSpeed, loadedChapters.size) {
        if (!autoPlaying) return@LaunchedEffect
        while (true) {
            val consumed = scrollState.scrollBy(
                ReaderAutoPlayPolicy.scrollStepPx(settings.autoScrollSpeed, density)
            )
            if (kotlin.math.abs(consumed) < 0.01f) {
                val lastLoadedChapter = loadedChapters.lastOrNull()?.index ?: -1
                if (lastLoadedChapter < state.chapters.lastIndex) {
                    onLoadNextChapter()
                    delay(300)
                } else {
                    onAutoPlayStopped()
                    break
                }
            } else {
                delay(16)
            }
        }
    }

    // 追踪当前所在章节与章节内行号
    LaunchedEffect(scrollState, loadedChapters) {
        snapshotFlow {
            val firstVisible = scrollState.firstVisibleItemIndex
            // 累积每个已加载章节的段落数，确定当前章节
            var offset = 0
            var currentIdx = loadedChapters.lastOrNull()?.index ?: 0
            var lineIndex = 0
            for ((chapterIndex, chapterItems) in chapterItemCounts) {
                if (firstVisible < offset + chapterItems) {
                    currentIdx = chapterIndex
                    lineIndex = (firstVisible - offset - 1).coerceAtLeast(0)
                    break
                }
                offset += chapterItems
            }
            ReaderVisiblePosition(
                chapterIndex = currentIdx,
                lineIndex = lineIndex,
                scrollOffset = scrollState.firstVisibleItemScrollOffset
            )
        }.distinctUntilChanged().collect { position ->
            onReadingPositionChanged(position.chapterIndex, position.lineIndex, position.scrollOffset)
        }
    }

    val configuration = LocalConfiguration.current
    val horizontalPadding = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        (((configuration.screenWidthDp - 760) / 2).coerceAtLeast(24) + 24).dp
    } else {
        38.dp
    }
    val verticalPadding = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 24.dp else 48.dp

    LazyColumn(
        state = scrollState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(autoPlaying) {
                if (autoPlaying) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        onAutoPlayStopped()
                    }
                }
            },
        userScrollEnabled = !touchLocked,
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(paragraphGap(settings.paragraphSpacing))
    ) {
        chapterParagraphs.forEach { (chapter, paragraphs) ->
            item(key = "title_${chapter.index}") {
                Text(
                    text = chapter.title,
                    color = foreground,
                    fontSize = 34.sp,
                    lineHeight = 44.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = fontFamily
                )
            }
            chapter.imageUri?.let { imageUri ->
                item(key = "image_${chapter.index}") {
                    ReaderChapterImage(
                        imageUri = imageUri,
                        title = chapter.title,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            items(paragraphs.size, key = { "p_${chapter.index}_$it" }) { index ->
                val highlight = state.highlights.firstOrNull { it.chapterIndex == chapter.index && it.lineIndex == index }
                Text(
                    text = paragraphs[index],
                    modifier = Modifier
                        .background(highlight?.let { Color(it.color) } ?: Color.Transparent)
                        .pointerInput(chapter.index, index, paragraphs[index]) {
                            detectTapGestures(
                                onTap = { onParagraphTap() },
                                onLongPress = { onParagraphLongPress(chapter, index, paragraphs[index]) }
                            )
                        },
                    color = foreground,
                    fontSize = (19 * settings.fontScale).sp,
                    lineHeight = (35 * settings.fontScale * settings.lineHeight).sp,
                    fontFamily = fontFamily,
                    letterSpacing = 0.sp
                    , style = if (state.book?.format == BookFormat.TXT && settings.indentTxtParagraphs && paragraphs[index].isNotBlank()) {
                        MaterialTheme.typography.bodyLarge.copy(textIndent = TextIndent(firstLine = 2.em))
                    } else MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun ReaderPagedContent(
    state: ReaderUiState,
    settings: ReaderSettings,
    foreground: Color,
    chapterParagraphs: List<Pair<ReaderChapter, List<String>>>,
    onLoadNextChapter: () -> Unit,
    onLoadPreviousChapter: () -> Unit,
    onReadingPositionChanged: (Int, Int, Int) -> Unit,
    touchLocked: Boolean,
    autoPlaying: Boolean,
    onAutoPlayStopped: () -> Unit
) {
    val pages = remember(
        chapterParagraphs,
        settings.fontScale,
        settings.lineHeight,
        settings.paragraphSpacing,
        settings.fontType,
        settings.customFontPath
    ) {
        buildReaderPages(chapterParagraphs, settings)
    }
    val fontFamily = rememberReaderFontFamily(settings)
    val pagerState = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })
    var restoredInitialPage by remember(state.book?.id, state.remoteBookId, settings.pageTurnMode) {
        mutableStateOf(false)
    }

    LaunchedEffect(state.loadedChapters.firstOrNull()?.index, pages.size, settings.pageTurnMode) {
        if (pages.isEmpty()) return@LaunchedEffect

        val savedProgress = state.book?.progress
        val loadedChapter = state.loadedChapters.firstOrNull()
        val canRestore = !restoredInitialPage &&
            savedProgress != null &&
            loadedChapter != null &&
            (savedProgress.chapterIndex == loadedChapter.index || savedProgress.chapterHref == loadedChapter.href)
        val targetChapterIndex = loadedChapter?.index ?: state.currentChapterIndex
        val targetLineIndex = if (canRestore) savedProgress?.lineIndex ?: 0 else 0
        val targetPage = pages.indexOfFirst {
            it.chapterIndex == targetChapterIndex && targetLineIndex in it.startLineIndex..it.endLineIndex
        }.takeIf { it >= 0 } ?: pages.indexOfFirst { it.chapterIndex == targetChapterIndex }.takeIf { it >= 0 } ?: 0

        pagerState.scrollToPage(targetPage)
        restoredInitialPage = true
    }

    LaunchedEffect(state.chapterWindowNavigation?.requestId) {
        val navigation = state.chapterWindowNavigation ?: return@LaunchedEffect
        val targetPage = pages.indexOfFirst {
            it.chapterIndex == navigation.chapterIndex &&
                navigation.lineIndex in it.startLineIndex..it.endLineIndex
        }.takeIf { it >= 0 }
            ?: pages.indexOfFirst { it.chapterIndex == navigation.chapterIndex }.takeIf { it >= 0 }
            ?: return@LaunchedEffect
        pagerState.scrollToPage(targetPage)
    }

    LaunchedEffect(pagerState, pages) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { pageIndex ->
                val page = pages.getOrNull(pageIndex) ?: return@collect
                onReadingPositionChanged(page.chapterIndex, page.startLineIndex, 0)
                if (pageIndex <= 1) {
                    onLoadPreviousChapter()
                }
                if (pageIndex >= pages.size - 2) {
                    onLoadNextChapter()
                }
            }
    }

    LaunchedEffect(autoPlaying, pages.size, settings.autoPageIntervalSeconds) {
        if (!autoPlaying || pages.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(ReaderAutoPlayPolicy.pageDelayMillis(settings.autoPageIntervalSeconds))
            val nextPage = pagerState.currentPage + 1
            if (nextPage < pages.size) {
                pagerState.animateScrollToPage(nextPage)
            } else {
                val lastLoadedChapter = state.loadedChapters.lastOrNull()?.index ?: -1
                if (lastLoadedChapter < state.chapters.lastIndex) {
                    onLoadNextChapter()
                    delay(300)
                } else {
                    onAutoPlayStopped()
                    break
                }
            }
        }
    }

    if (pages.isEmpty()) {
        Text(
            text = "暂无可阅读内容",
            modifier = Modifier.fillMaxSize().padding(38.dp),
            color = foreground
        )
        return
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(autoPlaying) {
                if (autoPlaying) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        onAutoPlayStopped()
                    }
                }
            },
        beyondViewportPageCount = 1,
        userScrollEnabled = !touchLocked
    ) { pageIndex ->
        val page = pages.getOrNull(pageIndex) ?: return@HorizontalPager
        val pageOffset = (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
        val transform = PageTurnVisuals.transform(settings.pageTurnMode, pageOffset)
        val configuration = LocalConfiguration.current
        val horizontalPadding = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            (((configuration.screenWidthDp - 760) / 2).coerceAtLeast(24) + 24).dp
        } else 38.dp
        val verticalPadding = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 24.dp else 48.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(transform.zIndex)
                .graphicsLayer {
                    alpha = transform.alpha
                    scaleX = transform.scale
                    scaleY = transform.scale
                    translationX = size.width * transform.translationXMultiplier
                    rotationY = transform.rotationY
                    cameraDistance = 10f * density
                    transformOrigin = TransformOrigin(
                        pivotFractionX = transform.pivotFractionX,
                        pivotFractionY = 0.5f
                    )
                }
                .drawWithContent {
                    drawContent()
                    if (settings.pageTurnMode == PageTurnMode.SIMULATION) {
                        val shadow = transform.shadowAlpha.coerceIn(0f, 0.5f)
                        val highlight = transform.highlightAlpha.coerceIn(0f, 0.35f)
                        if (shadow > 0f || highlight > 0f) {
                            val hingeAtStart = transform.pivotFractionX == 0f
                            drawRect(
                                brush = if (hingeAtStart) {
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.Black.copy(alpha = shadow),
                                            Color.Transparent,
                                            Color.White.copy(alpha = highlight)
                                        ),
                                        startX = 0f,
                                        endX = size.width
                                    )
                                } else {
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.White.copy(alpha = highlight),
                                            Color.Transparent,
                                            Color.Black.copy(alpha = shadow)
                                        ),
                                        startX = 0f,
                                        endX = size.width
                                    )
                                }
                            )
                        }
                    }
                }
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(paragraphGap(settings.paragraphSpacing))
        ) {
            if (page.title != null) {
                Text(
                    text = page.title,
                    color = foreground,
                        fontSize = 34.sp,
                        lineHeight = 44.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = fontFamily
                    )
                }
            if (page.imageUri != null) {
                ReaderChapterImage(
                    imageUri = page.imageUri,
                    title = page.title ?: "",
                    modifier = Modifier.fillMaxWidth()
                )
            }
            page.paragraphs.forEach { paragraph ->
                Text(
                    text = paragraph,
                    color = foreground,
                    fontSize = (19 * settings.fontScale).sp,
                    lineHeight = (35 * settings.fontScale * settings.lineHeight).sp,
                    fontFamily = fontFamily,
                    letterSpacing = 0.sp
                )
            }
        }
    }
}

private fun buildReaderPages(
    chapterParagraphs: List<Pair<ReaderChapter, List<String>>>,
    settings: ReaderSettings
): List<ReaderPageContent> {
    val charBudget = (760 / (settings.fontScale * settings.lineHeight))
        .toInt()
        .coerceIn(260, 920)
    val pages = mutableListOf<ReaderPageContent>()

    chapterParagraphs.forEach { (chapter, paragraphs) ->
        if (paragraphs.isEmpty()) {
            pages += ReaderPageContent(
                chapterIndex = chapter.index,
                startLineIndex = 0,
                endLineIndex = 0,
                title = chapter.title,
                paragraphs = emptyList(),
                imageUri = chapter.imageUri
            )
            return@forEach
        }

        var title: String? = chapter.title
        var startLineIndex = 0
        var charCount = 0
        val pageParagraphs = mutableListOf<String>()

        fun flush(endLineIndex: Int) {
            pages += ReaderPageContent(
                chapterIndex = chapter.index,
                startLineIndex = startLineIndex,
                endLineIndex = endLineIndex.coerceAtLeast(startLineIndex),
                title = title,
                paragraphs = pageParagraphs.toList(),
                imageUri = if (title != null) chapter.imageUri else null
            )
            title = null
            pageParagraphs.clear()
            charCount = 0
        }

        paragraphs.forEachIndexed { lineIndex, paragraph ->
            if (pageParagraphs.isNotEmpty() && charCount + paragraph.length > charBudget) {
                flush(lineIndex - 1)
                startLineIndex = lineIndex
            }
            if (pageParagraphs.isEmpty()) {
                startLineIndex = lineIndex
            }
            pageParagraphs += paragraph
            charCount += paragraph.length
        }

        if (pageParagraphs.isNotEmpty()) {
            flush(paragraphs.lastIndex)
        }
    }

    return pages
}

private fun paragraphGap(spacing: ParagraphSpacing) = when (spacing) {
    ParagraphSpacing.NONE -> 12.dp
    ParagraphSpacing.SMALL -> 22.dp
    ParagraphSpacing.LARGE -> 34.dp
}

@Composable
private fun ReaderChapterImage(
    imageUri: String,
    title: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(520.dp)
            .clip(RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageUri,
            contentDescription = title,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun ReaderBottomBar(
    progress: Float,
    currentChapterIndex: Int,
    totalChapters: Int,
    colors: ReaderPalette,
    onProgressChange: (Float) -> Unit,
    onOpenContents: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTheme: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    quickThemeLabel: String,
    onToggleQuickTheme: () -> Unit,
    onSelectChapter: (Int) -> Unit = {}
) {
    val navigationColor = colors.foreground
    val actionColor = colors.foreground.copy(alpha = 0.82f)
    val disabledColor = colors.muted.copy(alpha = 0.55f)
    val sliderTrackColor = colors.divider

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = 28.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
//        HorizontalDivider(color = divider)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "上一章",
                color = if (currentChapterIndex > 0) navigationColor else disabledColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = currentChapterIndex > 0
                ) {
                    onSelectChapter(currentChapterIndex - 1)
                }
            )
            Slider(
                value = progress.coerceIn(0f, 1f),
                onValueChange = onProgressChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 18.dp),
                colors = SliderDefaults.colors(
                    thumbColor = DesignTokens.Accent,
                    activeTrackColor = DesignTokens.Accent,
                    inactiveTrackColor = sliderTrackColor,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                )
            )
            Text(
                "下一章",
                color = if (currentChapterIndex < totalChapters - 1) navigationColor else disabledColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = currentChapterIndex < totalChapters - 1
                ) {
                    onSelectChapter(currentChapterIndex + 1)
                }
            )
        }
//        Text(
//            text = "${(progress * 100).toInt()}%",
//            modifier = Modifier.fillMaxWidth(),
//            textAlign = TextAlign.Center,
//            color = DesignTokens.Accent,
//            style = MaterialTheme.typography.titleMedium
//        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ReaderAction(Icons.AutoMirrored.Filled.FormatListBulleted, "目录", actionColor, onOpenContents)
            ReaderAction(
                icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                label = "收藏",
                color = if (isFavorite) DesignTokens.Accent else actionColor,
                onClick = onToggleFavorite
            )
            ReaderAction(Icons.Default.Settings, "设置", actionColor, onOpenSettings)
            ReaderAction(Icons.Default.Checkroom, "主题", actionColor, onOpenTheme)
            ReaderAction(
                icon = if (quickThemeLabel == "亮色") Icons.Default.LightMode else Icons.Default.DarkMode,
                label = quickThemeLabel,
                color = actionColor,
                onClick = onToggleQuickTheme
            )
        }
    }
}

@Composable
private fun ReaderAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(28.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ReaderContentsPage(
    state: ReaderUiState,
    onBack: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onChapterClick: (Int) -> Unit
) {
    val chapters = normalizedChapters(state)
    val colors = readerColors(state.settings.theme)
    var query by remember { mutableStateOf("") }
    val visibleChapters = remember(chapters, query) {
        if (query.isBlank()) chapters else chapters.filter { it.title.contains(query, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 28.dp, vertical = 25.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = colors.foreground)
            }
            Text(
                "目录",
                modifier = Modifier.weight(2f),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = colors.foreground
            )
            Text("共 ${chapters.size} 章", color = colors.muted)
            Text(
                "当前章节",
                color = DesignTokens.Accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onChapterClick(state.currentChapterIndex) }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenBookmarks)
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.BookmarkBorder, contentDescription = null, tint = colors.foreground)
                Text("书签 ${state.bookmarks.size}", color = colors.foreground)
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("搜索章节") }
        )
        if (query.isNotBlank()) {
            Text("找到 ${visibleChapters.size} 章", color = colors.muted, style = MaterialTheme.typography.labelMedium)
        }
//        Row(
//            modifier = Modifier.fillMaxWidth(),
//            horizontalArrangement = Arrangement.spacedBy(22.dp)
//        ) {
//            BookCover(title = state.book?.title ?: "三体", width = 98.dp, height = 138.dp)
//            Column(
//                modifier = Modifier
//                    .height(138.dp)
//                    .padding(top = 12.dp),
//                verticalArrangement = Arrangement.SpaceBetween
//            ) {
//                Column {
//                    Text(state.book?.title ?: "三体", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold)
//                    Text("刘慈欣 〉", color = DesignTokens.SoftText, style = MaterialTheme.typography.titleMedium)
//                    Text(
//                        "全集",
//                        color = DesignTokens.Accent,
//                        modifier = Modifier
//                            .padding(top = 10.dp)
//                            .background(DesignTokens.Accent.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
//                            .padding(horizontal = 12.dp, vertical = 6.dp)
//                    )
//                }
//            }
//        }
        if (state.settings.showContentsProgress) {
            ContentsProgressCard(state)
        }
//        Row(
//            modifier = Modifier.fillMaxWidth(),
//            horizontalArrangement = Arrangement.SpaceBetween,
//            verticalAlignment = Alignment.CenterVertically
//        ) {
////            Text(
////                state.book?.title ?: "无书名",
////                style = MaterialTheme.typography.titleLarge,
////                fontWeight = FontWeight.Bold,
////                maxLines = 1,
////                overflow = TextOverflow.Ellipsis,
////                modifier = Modifier.weight(1f)
////            )
//            Text("共 ${chapters.size} 章", color = DesignTokens.SoftText)
//        }
        when (state.settings.contentsStyle) {
            ReaderContentsStyle.CLASSIC -> ClassicContentsList(
                chapters = visibleChapters,
                state = state,
                colors = colors,
                onChapterClick = onChapterClick
            )

            ReaderContentsStyle.GROUPED -> GroupedContentsList(
                chapters = visibleChapters,
                currentChapterIndex = state.currentChapterIndex,
                errorMessage = state.errorMessage,
                colors = colors,
                onChapterClick = onChapterClick
            )
        }
    }
}

@Composable
private fun ClassicContentsList(
    chapters: List<ReaderChapter>,
    state: ReaderUiState,
    colors: ReaderPalette,
    onChapterClick: (Int) -> Unit
) {
    val initialIndex = remember(chapters, state.currentChapterIndex) {
        ReaderContentsCatalog.chapterListPosition(chapters, state.currentChapterIndex)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        if (chapters.isEmpty()) {
            item {
                ContentsEmptyState(state.errorMessage, colors)
            }
        } else {
            itemsIndexed(chapters) { _, chapter ->
                ChapterRow(
                    index = chapter.index,
                    title = chapter.title,
                    selected = chapter.index == state.currentChapterIndex,
                    locked = false,
                    progressText = chapterProgressText(chapter.index, state.currentChapterIndex, state.scrollProgress),
                    colors = colors,
                    onClick = { onChapterClick(chapter.index) }
                )
            }
        }
        item {
            ContentsLoadedFooter(chapters.isNotEmpty(), colors)
        }
    }
}

@Composable
private fun GroupedContentsList(
    chapters: List<ReaderChapter>,
    currentChapterIndex: Int,
    errorMessage: String?,
    colors: ReaderPalette,
    onChapterClick: (Int) -> Unit
) {
    val groups = remember(chapters) { ReaderContentsCatalog.group(chapters) }
    val currentGroupIndex = remember(groups, currentChapterIndex) {
        ReaderContentsCatalog.currentGroupIndex(groups, currentChapterIndex)
    }
    val expandedGroups = remember(groups, currentChapterIndex) {
        mutableStateMapOf<Int, Boolean>().apply {
            if (groups.isNotEmpty()) put(currentGroupIndex, true)
        }
    }
    val visibleItems = ReaderContentsCatalog.visibleItems(
        groups = groups,
        expandedGroupIndexes = expandedGroups.filterValues { it }.keys
    )
    val initialIndex = remember(visibleItems, currentChapterIndex) {
        ReaderContentsCatalog.visibleItemPosition(visibleItems, currentChapterIndex)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        if (groups.isEmpty()) {
            item {
                ContentsEmptyState(errorMessage, colors)
            }
        } else {
            itemsIndexed(
                items = visibleItems,
                key = { _, item ->
                    when (item) {
                        is ReaderContentsListItem.GroupHeader -> "group-${item.groupIndex}"
                        is ReaderContentsListItem.Chapter -> "chapter-${item.chapter.index}"
                    }
                }
            ) { _, item ->
                when (item) {
                    is ReaderContentsListItem.GroupHeader -> GroupedContentsHeader(
                        group = item.group,
                        expanded = item.expanded,
                        addTopSpacing = item.groupIndex > 0,
                        colors = colors,
                        onToggle = {
                            expandedGroups[item.groupIndex] = expandedGroups[item.groupIndex] != true
                        }
                    )

                    is ReaderContentsListItem.Chapter -> GroupedChapterRow(
                        chapter = item.chapter,
                        currentChapterIndex = currentChapterIndex,
                        isLast = item.isLast,
                        colors = colors,
                        onClick = { onChapterClick(item.chapter.index) }
                    )
                }
            }
        }
        item {
            ContentsLoadedFooter(groups.isNotEmpty(), colors)
        }
    }
}

@Composable
private fun GroupedContentsHeader(
    group: ReaderContentsGroup,
    expanded: Boolean,
    addTopSpacing: Boolean,
    colors: ReaderPalette,
    onToggle: () -> Unit
) {
    val shape = if (expanded) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    } else {
        RoundedCornerShape(16.dp)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (addTopSpacing) 12.dp else 0.dp),
        colors = CardDefaults.cardColors(containerColor = colors.background),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.divider),
        shape = shape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 18.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = group.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                color = colors.foreground,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "收起${group.title}" else "展开${group.title}",
                tint = colors.muted
            )
        }
    }
}

@Composable
private fun GroupedChapterRow(
    chapter: ReaderChapter,
    currentChapterIndex: Int,
    isLast: Boolean,
    colors: ReaderPalette,
    onClick: () -> Unit
) {
    val readState = ReaderContentsCatalog.readState(chapter.index, currentChapterIndex)
    val selected = readState == ReaderChapterReadState.CURRENT
    val mutedOrAccent = if (selected) DesignTokens.Accent else colors.muted
    val status = when (readState) {
        ReaderChapterReadState.READ -> "已读"
        ReaderChapterReadState.CURRENT -> "当前阅读"
        ReaderChapterReadState.UNREAD -> "未读"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (selected) 8.dp else 0.dp, vertical = if (selected) 4.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(if (selected) RoundedCornerShape(14.dp) else RoundedCornerShape(0.dp))
                .background(if (selected) DesignTokens.Accent.copy(alpha = 0.08f) else colors.background)
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (selected) DesignTokens.Accent else Color.Transparent)
            )
            Text(
                text = chapter.title.ifBlank { "第${chapter.index + 1}章" },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 18.dp, end = 14.dp),
                color = if (selected) DesignTokens.Accent else colors.foreground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = status,
                modifier = Modifier.padding(end = 18.dp),
                color = mutedOrAccent,
                style = MaterialTheme.typography.bodyMedium
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = mutedOrAccent.copy(alpha = if (selected) 1f else 0.55f),
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(22.dp)
            )
        }
        if (!selected && !isLast) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 22.dp),
                color = colors.divider.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
private fun ContentsEmptyState(errorMessage: String?, colors: ReaderPalette) {
    Text(
        text = errorMessage ?: "暂无目录，书籍解析完成后会显示章节列表",
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        color = colors.muted,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun ContentsLoadedFooter(hasChapters: Boolean, colors: ReaderPalette) {
    Text(
        if (hasChapters) "已加载全部章节" else "",
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        color = colors.muted,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun ReaderHighlightsPage(
    highlights: List<ReaderHighlight>,
    onBack: () -> Unit,
    onDelete: (ReaderHighlight) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
            Text("全部笔记", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Text("${highlights.size} 条", color = DesignTokens.SoftText)
        }
        if (highlights.isEmpty()) {
            Text("还没有高亮或批注", color = DesignTokens.SoftText, modifier = Modifier.fillMaxWidth().padding(top = 42.dp), textAlign = TextAlign.Center)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(highlights, key = { _, highlight -> highlight.id }) { _, highlight ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(highlight.color).copy(alpha = 0.25f))) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text(highlight.excerpt, fontWeight = FontWeight.Bold)
                                highlight.note?.takeIf { it.isNotBlank() }?.let { Text(it, color = DesignTokens.SoftText) }
                            }
                            IconButton(onClick = { onDelete(highlight) }) { Icon(Icons.Default.DeleteOutline, contentDescription = "删除笔记") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderBookmarksPage(
    bookmarks: List<ReaderBookmark>,
    onBack: () -> Unit,
    onBookmarkClick: (ReaderBookmark) -> Unit,
    onDelete: (ReaderBookmark) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回目录")
            }
            Text(
                text = "书签",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text("${bookmarks.size} 条", color = DesignTokens.SoftText)
        }

        if (bookmarks.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 72.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.BookmarkBorder,
                    contentDescription = null,
                    tint = DesignTokens.SoftText,
                    modifier = Modifier.size(44.dp)
                )
                Text("还没有书签", fontWeight = FontWeight.Bold)
                Text("阅读时打开底部工具栏，点击“书签”即可添加", color = DesignTokens.SoftText)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(bookmarks, key = { _, bookmark -> bookmark.id }) { _, bookmark ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onBookmarkClick(bookmark) },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F4F0)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Bookmark, contentDescription = null, tint = DesignTokens.Accent)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    bookmark.chapterTitle?.takeIf { it.isNotBlank() } ?: "正文",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${bookmark.progressLabel} · 第 ${bookmark.lineIndex + 1} 段",
                                    color = DesignTokens.SoftText,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(onClick = { onDelete(bookmark) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "删除书签", tint = DesignTokens.SoftText)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentsProgressCard(state: ReaderUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = DesignTokens.WarmCard),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("继续阅读", color = DesignTokens.Accent, fontWeight = FontWeight.Bold)
                Text(currentChapterTitle(state), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("本章进度 ${(state.scrollProgress * 100).toInt()}% · 已读 ${readingDurationLabel(state.book?.readingDurationSeconds ?: 0)}", color = DesignTokens.SoftText)
                WarmProgress(state.scrollProgress, Modifier.fillMaxWidth())
            }
            Spacer(
                Modifier
                    .width(1.dp)
                    .height(112.dp)
                    .background(DesignTokens.Hairline)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("全书进度", color = DesignTokens.SoftText)
                Text("${(state.scrollProgress * 100).toInt()}%", style = MaterialTheme.typography.displaySmall)
                Text("已读 ${state.currentChapterIndex + 1} / 共 ${state.chapters.size} 章", color = DesignTokens.SoftText)
                WarmProgress(state.scrollProgress, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ChapterRow(
    index: Int,
    title: String,
    selected: Boolean,
    locked: Boolean,
    progressText: String,
    colors: ReaderPalette,
    onClick: () -> Unit
) {
    val tint = when {
        locked -> colors.muted
        selected -> DesignTokens.Accent
        else -> colors.foreground
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) DesignTokens.Accent.copy(alpha = 0.08f) else colors.background
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) DesignTokens.Accent.copy(alpha = 0.18f) else colors.divider),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (locked) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            } else if (selected) {
                Icon(Icons.Default.Speed, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Text(
                text = title.ifBlank { "第${index + 1}章" },
                modifier = Modifier.weight(1f),
                color = tint,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(progressText, color = tint.copy(alpha = 0.75f))
            if (!locked) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = tint.copy(alpha = 0.55f))
            }
        }
    }
}

private enum class ReadingSettingsSection { TYPOGRAPHY, OPERATIONS }

@Composable
private fun ReadingSettingsPage(
    state: ReaderUiState,
    viewModel: ReaderViewModel,
    autoPlaying: Boolean,
    onStartAutoPlay: () -> Unit,
    onLockTouch: () -> Unit,
    onBack: () -> Unit
) {
    val settings = state.settings
    var section by remember { mutableStateOf(ReadingSettingsSection.TYPOGRAPHY) }
    var showFontDialog by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val fontImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.importFont(uri)
            showFontDialog = false
        }
    }

    LaunchedEffect(Unit) { viewModel.enterSettingsPage() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DesignTokens.AppBackground)
            .padding(horizontal = if (isLandscape) 28.dp else 20.dp, vertical = if (isLandscape) 12.dp else 24.dp)
    ) {
        SettingsPageHeader(
            "阅读设置",
            trailingText = "重置",
            trailingIcon = Icons.Default.Refresh,
            onBack = onBack,
            onTrailingClick = { viewModel.resetSettings() }
        )
        SegmentedSetting(
            title = "设置分类",
            options = listOf("排版", "操作"),
            selected = section.ordinal,
            onSelect = { section = ReadingSettingsSection.entries[it] }
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (isLandscape) 2 else 1),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (section == ReadingSettingsSection.TYPOGRAPHY) {
                item {
                    SettingsCard(title = "主题") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            readerThemeOptions().forEach { option ->
                                ThemeDot(option.label, option.color, settings.theme == option.theme) {
                                    viewModel.setTheme(option.theme)
                                }
                            }
                        }
                    }
                }
                item { SettingLineCard("字体", "${ReaderFontCatalog.selectedLabel(settings)} 〉") { showFontDialog = true } }
                item {
                    SliderCard("字号", "A-", "A+", (18f * settings.fontScale).toInt().toString(), settings.fontScale.coerceIn(14f / 18f, 30f / 18f), 14f / 18f..30f / 18f, viewModel::setFontScale)
                }
                item { SegmentedSetting("行距", listOf("紧凑", "适中", "宽松"), lineHeightToIndex(settings.lineHeight)) { viewModel.setLineHeight(indexToLineHeight(it)) } }
                item { SegmentedSetting("段距", listOf("无", "小", "大"), settings.paragraphSpacing.ordinal) { viewModel.setParagraphSpacing(ParagraphSpacing.entries[it]) } }
                item { IconSegmentedSetting(settings.textAlignment, viewModel::setTextAlignment) }
                item { SegmentedSetting("翻页方式", listOf("仿真", "滑动", "覆盖", "平移", "上下"), settings.pageTurnMode.ordinal) { viewModel.setPageTurnMode(PageTurnMode.entries[it]) } }
                item { SegmentedSetting("目录样式", listOf("经典", "分卷"), settings.contentsStyle.ordinal) { viewModel.setContentsStyle(ReaderContentsStyle.entries[it]) } }
                item {
                    SwitchSetting(
                        "显示目录阅读进度",
                        "在目录顶部显示阅读章节、进度与时长",
                        settings.showContentsProgress,
                        viewModel::setShowContentsProgress
                    )
                }
                if (state.book?.format == BookFormat.TXT) {
                    item { SwitchSetting("压缩 TXT 空行", "连续空白行最多保留一行", settings.compressTxtBlankLines, viewModel::setCompressTxtBlankLines) }
                    item { SwitchSetting("智能合并短行", "合并同段内被硬换行拆开的短句", settings.mergeTxtShortLines, viewModel::setMergeTxtShortLines) }
                    item { SwitchSetting("首行缩进", "正文段落首行缩进两个汉字", settings.indentTxtParagraphs, viewModel::setIndentTxtParagraphs) }
                }
                item { ScopeToggle(state.isBookSpecific, viewModel::setBookSpecific) }
            } else {
                item { SwitchSetting("跟随系统亮度", "关闭后使用阅读器独立亮度", settings.autoBrightness, viewModel::setAutoBrightness) }
                if (!settings.autoBrightness) {
                    item {
                        SliderCard("阅读亮度", "暗", "亮", "${(settings.brightness * 100).toInt()}%", settings.brightness, 0.1f..1f, viewModel::setBrightness)
                    }
                }
                item { SwitchSetting("屏幕常亮", "阅读时屏幕保持常亮状态", settings.screenAlwaysOn, viewModel::setScreenAlwaysOn) }
                item {
                    SegmentedSetting("屏幕方向", listOf("跟随系统", "竖屏", "横屏"), settings.orientationMode.ordinal) {
                        viewModel.setOrientationMode(ReaderOrientationMode.entries[it])
                    }
                }
                if (settings.pageTurnMode.usesPagedReading()) {
                    item {
                        SliderCard("自动翻页", "3秒", "30秒", "${settings.autoPageIntervalSeconds} 秒/页", settings.autoPageIntervalSeconds.toFloat(), 3f..30f) {
                            viewModel.setAutoPageIntervalSeconds(it.toInt())
                        }
                    }
                } else {
                    item {
                        SegmentedSetting("滚动速度", listOf("慢", "中", "快"), settings.autoScrollSpeed.ordinal) {
                            viewModel.setAutoScrollSpeed(ReaderAutoScrollSpeed.entries[it])
                        }
                    }
                }
                item {
                    SettingLineCard("自动阅读", if (autoPlaying) "正在运行 〉" else "开始 〉", onClick = onStartAutoPlay)
                }
                item { SettingLineCard("锁定触摸", "立即锁定 〉", onClick = onLockTouch) }
                item {
                    Text("自动阅读时将临时保持屏幕常亮；打开其他面板或进入后台会自动暂停。", color = DesignTokens.SoftText, modifier = Modifier.padding(8.dp))
                }
            }
        }
    }

    if (showFontDialog) {
        FontSelectionDialog(
            settings = settings,
            onSelect = { viewModel.setFontType(it); showFontDialog = false },
            onImport = { fontImportLauncher.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream")) },
            onDismiss = { showFontDialog = false }
        )
    }
}

private data class ReaderThemeOption(
    val label: String,
    val color: Color,
    val theme: ReaderTheme
)

private fun readerThemeOptions() = listOf(
    ReaderThemeOption("白色", Color.White, ReaderTheme.LIGHT),
    ReaderThemeOption("米纸", Color(0xFFF2E4CE), ReaderTheme.PAPER),
    ReaderThemeOption("护眼绿", Color(0xFFD3E8C9), ReaderTheme.GREEN),
    ReaderThemeOption("深灰", Color(0xFF4B4B4B), ReaderTheme.GRAY),
    ReaderThemeOption("夜间", Color(0xFF101010), ReaderTheme.DARK)
)

@Composable
private fun ScopeToggle(
    isBookSpecific: Boolean,
    onScopeChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, DesignTokens.Hairline),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("设置范围", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier
                    .height(40.dp)
                    .background(Color(0xFFF3F1EF), RoundedCornerShape(12.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .fillMaxHeight()
                        .background(
                            if (!isBookSpecific) Color.White else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                        .then(
                            if (!isBookSpecific) Modifier.border(1.dp, DesignTokens.Accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                            else Modifier
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onScopeChange(false) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("全局默认", color = if (!isBookSpecific) DesignTokens.Accent else DesignTokens.SoftText)
                }
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .fillMaxHeight()
                        .background(
                            if (isBookSpecific) Color.White else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                        .then(
                            if (isBookSpecific) Modifier.border(1.dp, DesignTokens.Accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                            else Modifier
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onScopeChange(true) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("本书设置", color = if (isBookSpecific) DesignTokens.Accent else DesignTokens.SoftText)
                }
            }
        }
    }
}

@Composable
private fun SettingsPageHeader(
    title: String,
    trailingText: String? = null,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onBack: () -> Unit,
    onTrailingClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Text(
            title,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier
                .width(82.dp)
                .then(if (onTrailingClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTrailingClick
                ) else Modifier),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            trailingIcon?.let { Icon(it, null, tint = DesignTokens.SoftText) }
            trailingText?.let { Text(it, color = DesignTokens.SoftText) }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, DesignTokens.Hairline),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun ThemeDot(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Canvas(
                modifier = Modifier
                    .size(48.dp)
                    .border(1.dp, if (selected) DesignTokens.Accent else Color.Transparent, CircleShape)
                    .padding(3.dp)
            ) {
                drawCircle(color)
                if (color == Color.White) {
                    drawCircle(Color(0xFFEDE8E2), style = Stroke(width = 2f))
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(DesignTokens.Accent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
        Text(label, color = DesignTokens.SoftText)
    }
}

@Composable
private fun SettingLineCard(title: String, trailing: String, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, DesignTokens.Hairline),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(trailing, color = DesignTokens.SoftText, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SliderCard(
    title: String,
    leading: String,
    trailing: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, DesignTokens.Hairline),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(leading, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = DesignTokens.SoftText)
                Slider(
                    value = value,
                    onValueChange = onValueChange,
                    valueRange = range,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 18.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = DesignTokens.Accent,
                        activeTrackColor = DesignTokens.Accent,
                        inactiveTrackColor = DesignTokens.Hairline
                    )
                )
                Text(trailing, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = DesignTokens.SoftText)
            }
            Text(valueLabel, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = DesignTokens.SoftText)
        }
    }
}

@Composable
private fun SegmentedSetting(
    title: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, DesignTokens.Hairline),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(title, modifier = Modifier.width(106.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .background(Color(0xFFF3F1EF), RoundedCornerShape(14.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                options.forEachIndexed { index, option ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(
                                if (index == selected) Color.White else Color.Transparent,
                                RoundedCornerShape(12.dp)
                            )
                            .then(
                                if (index == selected) Modifier.border(1.dp, DesignTokens.Accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                else Modifier
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelect(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(option, color = if (index == selected) DesignTokens.Accent else DesignTokens.SoftText)
                    }
                }
            }
        }
    }
}

@Composable
private fun IconSegmentedSetting(
    selected: TextAlignment,
    onSelect: (TextAlignment) -> Unit
) {
    val alignments = listOf(
        TextAlignment.LEFT to Icons.AutoMirrored.Filled.FormatAlignLeft,
        TextAlignment.CENTER to Icons.Filled.FormatAlignCenter,
        TextAlignment.RIGHT to Icons.AutoMirrored.Filled.FormatAlignRight,
        TextAlignment.JUSTIFY to Icons.Filled.FormatAlignJustify
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, DesignTokens.Hairline),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("对齐方式", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            alignments.forEachIndexed { index, (alignment, icon) ->
                Box(
                    modifier = Modifier
                        .padding(start = if (index == 0) 0.dp else 12.dp)
                        .size(54.dp, 42.dp)
                        .background(if (alignment == selected) Color.White else Color(0xFFF3F1EF), RoundedCornerShape(12.dp))
                        .border(
                            1.dp,
                            if (alignment == selected) DesignTokens.Accent.copy(alpha = 0.45f) else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(alignment) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = if (alignment == selected) DesignTokens.Accent else DesignTokens.SoftText)
                }
            }
        }
    }
}

@Composable
private fun SwitchSetting(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, DesignTokens.Hairline),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(subtitle, color = DesignTokens.SoftText)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedTrackColor = DesignTokens.Accent)
            )
        }
    }
}

@Composable
private fun ThemeSettingsPage(
    appThemeMode: AppThemeMode,
    accentColor: AccentColor,
    readerTheme: ReaderTheme,
    onBack: () -> Unit,
    onAppThemeModeChange: (AppThemeMode) -> Unit,
    onAccentColorChange: (AccentColor) -> Unit,
    onReaderThemeChange: (ReaderTheme) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 30.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        SettingsPageHeader("页面主题", onBack = onBack)

        // App Theme Mode
        Text("外观模式", style = MaterialTheme.typography.titleLarge)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf(
                    AppThemeMode.SYSTEM to "跟随系统",
                    AppThemeMode.LIGHT to "浅色",
                    AppThemeMode.DARK to "深色"
                ).forEach { (mode, label) ->
                    val selected = appThemeMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .background(
                                if (selected) DesignTokens.Accent else Color.Transparent,
                                RoundedCornerShape(12.dp)
                            )
                            .border(
                                1.dp,
                                if (selected) DesignTokens.Accent else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onAppThemeModeChange(mode) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Accent Color
        Text("主题配色", style = MaterialTheme.typography.titleLarge)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AccentColor.entries.forEach { color ->
                    val selected = accentColor == color
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(color.colorValue), CircleShape)
                            .border(
                                3.dp,
                                if (selected) Color(color.colorValue).copy(alpha = 0.35f) else Color.Transparent,
                                CircleShape
                            )
                            .clickable { onAccentColorChange(color) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) Icon(Icons.Default.Check, null, tint = Color.White)
                    }
                }
            }
        }

        // Reader Theme
        Text("阅读主题", style = MaterialTheme.typography.titleLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ThemePreviewCard(
                label = "明亮",
                selected = readerTheme == ReaderTheme.LIGHT,
                dark = false,
                modifier = Modifier.weight(1f)
            ) {
                onReaderThemeChange(ReaderTheme.LIGHT)
            }
            ThemePreviewCard(
                label = "纸张",
                selected = readerTheme == ReaderTheme.PAPER,
                dark = false,
                modifier = Modifier.weight(1f)
            ) {
                onReaderThemeChange(ReaderTheme.PAPER)
            }
            ThemePreviewCard(
                label = "护眼",
                selected = readerTheme == ReaderTheme.GREEN,
                dark = false,
                modifier = Modifier.weight(1f)
            ) {
                onReaderThemeChange(ReaderTheme.GREEN)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ThemePreviewCard(
                label = "灰色",
                selected = readerTheme == ReaderTheme.GRAY,
                dark = true,
                modifier = Modifier.weight(1f)
            ) {
                onReaderThemeChange(ReaderTheme.GRAY)
            }
            ThemePreviewCard(
                label = "深色",
                selected = readerTheme == ReaderTheme.DARK,
                dark = true,
                modifier = Modifier.weight(1f)
            ) {
                onReaderThemeChange(ReaderTheme.DARK)
            }
            Spacer(Modifier.weight(1f))
        }

        // Preview
        Text("效果预览", style = MaterialTheme.typography.titleLarge)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BookCover("三体（全集）", width = 86.dp, height = 122.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("三体（全集）", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("刘慈欣", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("阅读进度 42%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    WarmProgress(0.42f, Modifier.fillMaxWidth())
                    Text("上次阅读：昨天 22:15", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "继续阅读",
                    modifier = Modifier
                        .background(Brush.horizontalGradient(listOf(DesignTokens.Accent, DesignTokens.AccentDark)), RoundedCornerShape(22.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ReaderThemePage(
    settings: ReaderSettings,
    onBack: () -> Unit,
    onThemeChange: (ReaderTheme) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 24.dp, vertical = 30.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        SettingsPageHeader("阅读主题", onBack = onBack)
        Text("选择阅读主题", style = MaterialTheme.typography.titleLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ThemePreviewCard(
                label = "明亮",
                selected = settings.theme == ReaderTheme.LIGHT,
                dark = false,
                modifier = Modifier.weight(1f)
            ) {
                onThemeChange(ReaderTheme.LIGHT)
            }
            ThemePreviewCard(
                label = "纸张",
                selected = settings.theme == ReaderTheme.PAPER,
                dark = false,
                modifier = Modifier.weight(1f)
            ) {
                onThemeChange(ReaderTheme.PAPER)
            }
            ThemePreviewCard(
                label = "护眼",
                selected = settings.theme == ReaderTheme.GREEN,
                dark = false,
                modifier = Modifier.weight(1f)
            ) {
                onThemeChange(ReaderTheme.GREEN)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ThemePreviewCard(
                label = "灰色",
                selected = settings.theme == ReaderTheme.GRAY,
                dark = true,
                modifier = Modifier.weight(1f)
            ) {
                onThemeChange(ReaderTheme.GRAY)
            }
            ThemePreviewCard(
                label = "深色",
                selected = settings.theme == ReaderTheme.DARK,
                dark = true,
                modifier = Modifier.weight(1f)
            ) {
                onThemeChange(ReaderTheme.DARK)
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ThemePreviewCard(
    label: String,
    selected: Boolean,
    dark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            modifier = Modifier
                .height(190.dp)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = if (dark) Color(0xFF151819) else Color(0xFFFFFCF8)),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) DesignTokens.Accent else DesignTokens.Hairline)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("书架", color = if (dark) Color.White else Color(0xFF1F1D1A), fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BookCover("三体", width = 34.dp, height = 50.dp)
                    Column {
                        Text("三体（全集）", color = if (dark) Color.White else Color(0xFF1F1D1A), fontSize = 10.sp)
                        Text("阅读进度 42%", color = if (dark) Color(0xFFB7B2AB) else DesignTokens.SoftText, fontSize = 8.sp)
                    }
                }
                WarmProgress(0.42f, Modifier.fillMaxWidth())
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    Text("书架", color = DesignTokens.Accent, fontSize = 9.sp)
                    Text("书城", color = if (dark) Color(0xFFD0CCC7) else DesignTokens.SoftText, fontSize = 9.sp)
                    Text("发现", color = if (dark) Color(0xFFD0CCC7) else DesignTokens.SoftText, fontSize = 9.sp)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (selected) {
                Icon(Icons.Default.Check, null, tint = DesignTokens.Accent, modifier = Modifier.size(22.dp))
            }
        }
    }
}

private data class ReaderPalette(
    val background: Color,
    val foreground: Color,
    val muted: Color,
    val divider: Color
)

private fun readerColors(theme: ReaderTheme): ReaderPalette = when (theme) {
    ReaderTheme.LIGHT -> ReaderPalette(
        background = Color(0xFFFFFCF8),
        foreground = Color(0xFF201E1B),
        muted = Color(0xFF56524E),
        divider = Color(0xFFE9DDD1)
    )
    ReaderTheme.PAPER -> ReaderPalette(
        background = Color(0xFFFAF3E0),
        foreground = Color(0xFF2D2924),
        muted = Color(0xFF6B6257),
        divider = Color(0xFFE2D1BE)
    )
    ReaderTheme.GREEN -> ReaderPalette(
        background = Color(0xFFEAF5E5),
        foreground = Color(0xFF1F2C22),
        muted = Color(0xFF58705C),
        divider = Color(0xFFCFE0C8)
    )
    ReaderTheme.GRAY -> ReaderPalette(
        background = Color(0xFF3D3D3D),
        foreground = Color(0xFFE8E3DC),
        muted = Color(0xFFC8C0B8),
        divider = Color(0xFF5A5651)
    )
    ReaderTheme.DARK -> ReaderPalette(
        background = Color(0xFF151515),
        foreground = Color(0xFFE8E1D4),
        muted = Color(0xFFC0B9AD),
        divider = Color(0xFF34302C)
    )
}

private fun currentChapterTitle(state: ReaderUiState): String =
    state.chapters.getOrNull(state.currentChapterIndex)?.title
        ?: state.book?.progress?.chapterTitle
        ?: "第一章 科学边界"

private fun normalizedChapters(state: ReaderUiState): List<ReaderChapter> {
    if (state.chapters.isNotEmpty()) return state.chapters
    if (state.book != null || state.errorMessage != null || state.isLoading) return emptyList()
    return listOf(
        ReaderChapter(index = 0, title = "第一章 科学边界", href = "sample-0", content = ""),
        ReaderChapter(index = 1, title = "第二章 倒计时", href = "sample-1", content = ""),
        ReaderChapter(index = 2, title = "第三章 射手和农场主", href = "sample-2", content = ""),
        ReaderChapter(index = 3, title = "第四章 三体游戏", href = "sample-3", content = ""),
        ReaderChapter(index = 4, title = "第五章 逃逸速度", href = "sample-4", content = ""),
        ReaderChapter(index = 5, title = "第六章 智子", href = "sample-5", content = ""),
        ReaderChapter(index = 6, title = "第七章 太空军舰", href = "sample-6", content = ""),
        ReaderChapter(index = 7, title = "第八章 古筝行动", href = "sample-7", content = ""),
        ReaderChapter(index = 8, title = "第九章 远征", href = "sample-8", content = ""),
        ReaderChapter(index = 9, title = "第十章 乱纪元", href = "sample-9", content = "")
    )
}

private fun chapterProgressText(index: Int, currentIndex: Int, progress: Float): String = when {
    index < currentIndex -> "已读"
    index == currentIndex -> "${(progress * 100).toInt()}%"
    index > 5 -> "未读"
    else -> "未读"
}

private fun readingDurationLabel(seconds: Long): String {
    val minutes = (seconds.coerceAtLeast(0) / 60)
    return if (minutes >= 60) "${minutes / 60}小时${minutes % 60}分钟" else "${minutes}分钟"
}

private fun lineHeightToIndex(lineHeight: Float): Int = when {
    lineHeight < 1.3f -> 0
    lineHeight > 1.6f -> 2
    else -> 1
}

private fun indexToLineHeight(index: Int): Float = when (index) {
    0 -> 1.2f
    1 -> 1.45f
    2 -> 1.8f
    else -> 1.45f
}

@Composable
private fun FontSelectionDialog(
    settings: ReaderSettings,
    onSelect: (ReaderFontType) -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("选择字体", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("选择阅读正文使用的字体。导入字体会复制到应用私有目录。", color = DesignTokens.SoftText)
                ReaderFontCatalog.builtInFonts.forEach { option ->
                    FontOptionCard(
                        title = option.label,
                        subtitle = option.description,
                        selected = settings.fontType == option.type,
                        onClick = { onSelect(option.type) }
                    )
                }
                if (!settings.customFontPath.isNullOrBlank()) {
                    FontOptionCard(
                        title = settings.customFontName ?: "本地导入字体",
                        subtitle = "从本地文件导入",
                        selected = settings.fontType == ReaderFontType.CUSTOM,
                        onClick = { onSelect(ReaderFontType.CUSTOM) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onImport) {
                Text("导入本地字体", color = DesignTokens.Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = DesignTokens.SoftText)
            }
        }
    )
}

@Composable
private fun FontOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) DesignTokens.WarmCard else Color.White
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) DesignTokens.Accent.copy(alpha = 0.42f) else DesignTokens.Hairline
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                if (selected) Icons.Default.CheckCircle else Icons.Default.Check,
                null,
                tint = if (selected) DesignTokens.Accent else DesignTokens.SoftText
            )
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, color = DesignTokens.SoftText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun rememberReaderFontFamily(settings: ReaderSettings): FontFamily {
    return remember(settings.fontType, settings.customFontPath) {
        when (settings.fontType) {
            ReaderFontType.SYSTEM -> FontFamily.Default
            ReaderFontType.SERIF -> FontFamily.Serif
            ReaderFontType.SANS_SERIF -> FontFamily.SansSerif
            ReaderFontType.MONOSPACE -> FontFamily.Monospace
            ReaderFontType.CUSTOM -> {
                val path = settings.customFontPath
                val file = path?.let { File(it) }
                if (file != null && file.exists()) {
                    runCatching { FontFamily(Typeface.createFromFile(file)) }.getOrDefault(FontFamily.Default)
                } else {
                    FontFamily.Default
                }
            }
        }
    }
}
