package com.aibook.android.feature.reader

import android.graphics.Typeface
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aibook.android.core.model.AccentColor
import com.aibook.android.core.model.AppThemeMode
import com.aibook.android.core.model.PageTurnMode
import com.aibook.android.core.model.ParagraphSpacing
import com.aibook.android.core.model.ReaderContentsStyle
import com.aibook.android.core.model.ReaderFontCatalog
import com.aibook.android.core.model.ReaderFontType
import com.aibook.android.core.model.ReaderSettings
import com.aibook.android.core.model.ReaderTheme
import com.aibook.android.core.model.TextAlignment
import com.aibook.android.core.model.usesPagedReading
import com.aibook.android.feature.settings.SettingsViewModel
import com.aibook.android.core.reader.ReaderChapter
import com.aibook.android.core.reader.ReaderBookmark
import com.aibook.android.ui.design.BookCover
import com.aibook.android.ui.design.DesignTokens
import com.aibook.android.ui.design.WarmProgress
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import java.io.File

private enum class ReaderPanel {
    None,
    Contents,
    Bookmarks,
    Settings,
    Theme
}

private data class ReaderVisiblePosition(
    val chapterIndex: Int,
    val lineIndex: Int,
    val scrollOffset: Int
)

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
    // 提升 scrollState 到此处，避免打开目录/设置后返回时丢失滚动位置
    val scrollState = rememberLazyListState()

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
        ReaderPanel.Settings -> ReadingSettingsPage(
            state = state,
            viewModel = viewModel,
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
            onToggleFavorite = viewModel::toggleFavorite,
            onToggleBookmark = viewModel::toggleBookmark,
            onSelectChapter = viewModel::selectChapter,
            onReadingPositionChanged = viewModel::updateReadingPosition
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
    onToggleFavorite: () -> Unit,
    onToggleBookmark: () -> Unit,
    onSelectChapter: (Int) -> Unit,
    onReadingPositionChanged: (Int, Int, Int) -> Unit
) {
    val colors = readerColors(settings.theme)
    var controlsVisible by remember(state.book?.id) { mutableStateOf(false) }
    val readerTapInteractionSource = remember { MutableInteractionSource() }

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
                .pointerInput(Unit) {
                    detectTapGestures {
                        controlsVisible = !controlsVisible
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
                    onReadingPositionChanged = onReadingPositionChanged
                )
                else -> Text(
                    text = "暂无可阅读内容",
                    modifier = Modifier.align(Alignment.Center),
                    color = colors.muted
                )
            }
        }
        if (controlsVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                ReaderBottomBar(
                    progress = state.scrollProgress,
                    currentChapterIndex = state.currentChapterIndex,
                    totalChapters = state.chapters.size,
                    foreground = colors.foreground,
                    muted = colors.muted,
                    divider = colors.divider,
                    onProgressChange = onProgressChange,
                    onOpenContents = onOpenContents,
                    onOpenSettings = onOpenSettings,
                    onOpenTheme = onOpenTheme,
                    isFavorite = state.book?.favorite ?: false,
                    onToggleFavorite = onToggleFavorite,
                    isBookmarked = state.isCurrentPositionBookmarked,
                    onToggleBookmark = onToggleBookmark,
                    onSelectChapter = onSelectChapter
                )
            }
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
    onReadingPositionChanged: (Int, Int, Int) -> Unit
) {
    val loadedChapters = state.loadedChapters
    val chapterParagraphs = remember(loadedChapters) {
        loadedChapters.map { chapter ->
            chapter to chapter.content.split("\n").filter { it.isNotBlank() }
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
            onReadingPositionChanged = onReadingPositionChanged
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

    LazyColumn(
        state = scrollState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 38.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
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
                Text(
                    text = paragraphs[index],
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

@Composable
private fun ReaderPagedContent(
    state: ReaderUiState,
    settings: ReaderSettings,
    foreground: Color,
    chapterParagraphs: List<Pair<ReaderChapter, List<String>>>,
    onLoadNextChapter: () -> Unit,
    onReadingPositionChanged: (Int, Int, Int) -> Unit
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

    LaunchedEffect(pagerState, pages) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { pageIndex ->
                val page = pages.getOrNull(pageIndex) ?: return@collect
                onReadingPositionChanged(page.chapterIndex, page.startLineIndex, 0)
                if (pageIndex >= pages.size - 2) {
                    onLoadNextChapter()
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
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1
    ) { pageIndex ->
        val page = pages.getOrNull(pageIndex) ?: return@HorizontalPager
        val pageOffset = (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
        val transform = PageTurnVisuals.transform(settings.pageTurnMode, pageOffset)
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
                .padding(horizontal = 38.dp, vertical = 48.dp),
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
    foreground: Color,
    muted: Color,
    divider: Color,
    onProgressChange: (Float) -> Unit,
    onOpenContents: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTheme: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    isBookmarked: Boolean = false,
    onToggleBookmark: () -> Unit = {},
    onSelectChapter: (Int) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
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
                color = if (currentChapterIndex > 0) foreground else divider,
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
                    inactiveTrackColor = divider,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                )
            )
            Text(
                "下一章",
                color = if (currentChapterIndex < totalChapters - 1) foreground else divider,
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
            ReaderAction(Icons.AutoMirrored.Filled.FormatListBulleted, "目录", muted, onOpenContents)
            ReaderAction(
                icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                label = "收藏",
                color = if (isFavorite) DesignTokens.Accent else muted,
                onClick = onToggleFavorite
            )
            ReaderAction(Icons.Default.Settings, "设置", muted, onOpenSettings)
            ReaderAction(Icons.Default.Checkroom, "主题", muted, onOpenTheme)
            ReaderAction(
                icon = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                label = if (isBookmarked) "取消书签" else "书签",
                color = if (isBookmarked) DesignTokens.Accent else muted,
                onClick = onToggleBookmark
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp, vertical = 25.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                "目录",
                modifier = Modifier.weight(2f),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text("共 ${chapters.size} 章", color = DesignTokens.SoftText)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenBookmarks)
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.BookmarkBorder, contentDescription = null)
                Text("书签 ${state.bookmarks.size}")
            }
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
//        ContentsProgressCard(state)
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
                chapters = chapters,
                state = state,
                onChapterClick = onChapterClick
            )

            ReaderContentsStyle.GROUPED -> GroupedContentsList(
                chapters = chapters,
                currentChapterIndex = state.currentChapterIndex,
                errorMessage = state.errorMessage,
                onChapterClick = onChapterClick
            )
        }
    }
}

@Composable
private fun ClassicContentsList(
    chapters: List<ReaderChapter>,
    state: ReaderUiState,
    onChapterClick: (Int) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        if (chapters.isEmpty()) {
            item {
                ContentsEmptyState(state.errorMessage)
            }
        } else {
            itemsIndexed(chapters) { index, chapter ->
                ChapterRow(
                    index = index,
                    title = chapter.title,
                    selected = index == state.currentChapterIndex,
                    locked = false,
                    progressText = chapterProgressText(index, state.currentChapterIndex, state.scrollProgress),
                    onClick = { onChapterClick(index) }
                )
            }
        }
        item {
            ContentsLoadedFooter(chapters.isNotEmpty())
        }
    }
}

@Composable
private fun GroupedContentsList(
    chapters: List<ReaderChapter>,
    currentChapterIndex: Int,
    errorMessage: String?,
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

    LazyColumn(
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        if (groups.isEmpty()) {
            item {
                ContentsEmptyState(errorMessage)
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
                        onToggle = {
                            expandedGroups[item.groupIndex] = expandedGroups[item.groupIndex] != true
                        }
                    )

                    is ReaderContentsListItem.Chapter -> GroupedChapterRow(
                        chapter = item.chapter,
                        currentChapterIndex = currentChapterIndex,
                        isLast = item.isLast,
                        onClick = { onChapterClick(item.chapter.index) }
                    )
                }
            }
        }
        item {
            ContentsLoadedFooter(groups.isNotEmpty())
        }
    }
}

@Composable
private fun GroupedContentsHeader(
    group: ReaderContentsGroup,
    expanded: Boolean,
    addTopSpacing: Boolean,
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, DesignTokens.Hairline),
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
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "收起${group.title}" else "展开${group.title}",
                tint = DesignTokens.SoftText
            )
        }
    }
}

@Composable
private fun GroupedChapterRow(
    chapter: ReaderChapter,
    currentChapterIndex: Int,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val readState = ReaderContentsCatalog.readState(chapter.index, currentChapterIndex)
    val selected = readState == ReaderChapterReadState.CURRENT
    val mutedOrAccent = if (selected) DesignTokens.Accent else DesignTokens.SoftText
    val status = when (readState) {
        ReaderChapterReadState.READ -> "已读"
        ReaderChapterReadState.CURRENT -> "当前阅读"
        ReaderChapterReadState.UNREAD -> "未读"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, DesignTokens.Hairline),
        shape = if (isLast) {
            RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
        } else {
            RoundedCornerShape(0.dp)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .background(if (selected) DesignTokens.Accent.copy(alpha = 0.08f) else Color.Transparent)
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(if (selected) DesignTokens.Accent else Color.Transparent)
            )
            Text(
                text = (chapter.index + 1).toString().padStart(2, '0'),
                modifier = Modifier.padding(start = 14.dp),
                color = mutedOrAccent,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = chapter.title.ifBlank { "第${chapter.index + 1}章" },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 18.dp),
                color = if (selected) DesignTokens.Accent else MaterialTheme.colorScheme.onSurface,
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
        }
    }
}

@Composable
private fun ContentsEmptyState(errorMessage: String?) {
    Text(
        text = errorMessage ?: "暂无目录，书籍解析完成后会显示章节列表",
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        color = DesignTokens.SoftText,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun ContentsLoadedFooter(hasChapters: Boolean) {
    Text(
        if (hasChapters) "已加载全部章节" else "",
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        color = DesignTokens.SoftText,
        textAlign = TextAlign.Center
    )
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
                Text("本章进度 ${(state.scrollProgress * 100).toInt()}%", color = DesignTokens.SoftText)
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
                Text("已读 14.2 万字 / 共 49.8 万字", color = DesignTokens.SoftText)
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
    onClick: () -> Unit
) {
    val tint = when {
        locked -> DesignTokens.SoftText
        selected -> DesignTokens.Accent
        else -> Color(0xFF282522)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) DesignTokens.WarmCard else Color.White
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) DesignTokens.Accent.copy(alpha = 0.18f) else DesignTokens.Hairline),
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

@Composable
private fun ReadingSettingsPage(
    state: ReaderUiState,
    viewModel: ReaderViewModel,
    onBack: () -> Unit
) {
    val settings = state.settings
    var showFontDialog by remember { mutableStateOf(false) }
    val fontImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.importFont(uri)
            showFontDialog = false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.enterSettingsPage()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DesignTokens.AppBackground)
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        SettingsPageHeader(
            "阅读设置",
            trailingText = "重置",
            trailingIcon = Icons.Default.Refresh,
            onBack = onBack,
            onTrailingClick = { viewModel.resetSettings() }
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsCard(title = "主题") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        readerThemeOptions().forEach { option ->
                            ThemeDot(
                                label = option.label,
                                color = option.color,
                                selected = settings.theme == option.theme
                            ) {
                                viewModel.setTheme(option.theme)
                            }
                        }
                    }
                }
            }
            item {
                SettingLineCard(
                    "字体",
                    trailing = "${ReaderFontCatalog.selectedLabel(settings)} 〉",
                    onClick = { showFontDialog = true }
                )
            }
            item {
                SliderCard(
                    title = "字号",
                    leading = "A-",
                    trailing = "A+",
                    valueLabel = (18f * settings.fontScale).toInt().toString(),
                    value = settings.fontScale.coerceIn(14f / 18f, 30f / 18f),
                    range = 14f / 18f..30f / 18f,
                    onValueChange = viewModel::setFontScale
                )
            }
            item {
                SegmentedSetting(
                    "行距",
                    listOf("紧凑", "适中", "宽松"),
                    selected = lineHeightToIndex(settings.lineHeight),
                    onSelect = { viewModel.setLineHeight(indexToLineHeight(it)) }
                )
            }
            item {
                SegmentedSetting(
                    "段距",
                    listOf("无", "小", "大"),
                    selected = settings.paragraphSpacing.ordinal,
                    onSelect = { viewModel.setParagraphSpacing(ParagraphSpacing.entries[it]) }
                )
            }
            item {
                IconSegmentedSetting(
                    selected = settings.textAlignment,
                    onSelect = viewModel::setTextAlignment
                )
            }
            item {
                SegmentedSetting(
                    "翻页方式",
                    listOf("仿真", "滑动", "覆盖", "平移", "上下"),
                    selected = settings.pageTurnMode.ordinal,
                    onSelect = { viewModel.setPageTurnMode(PageTurnMode.entries[it]) }
                )
            }
            item {
                SegmentedSetting(
                    "目录样式",
                    listOf("经典", "分卷"),
                    selected = settings.contentsStyle.ordinal,
                    onSelect = { viewModel.setContentsStyle(ReaderContentsStyle.entries[it]) }
                )
            }
            item {
                SwitchSetting(
                    "自动亮度",
                    "根据环境光线自动调节屏幕亮度",
                    checked = settings.autoBrightness,
                    onCheckedChange = viewModel::setAutoBrightness
                )
            }
            item {
                SwitchSetting(
                    "屏幕常亮",
                    "阅读时屏幕保持常亮状态",
                    checked = settings.screenAlwaysOn,
                    onCheckedChange = viewModel::setScreenAlwaysOn
                )
            }
            item {
                ScopeToggle(
                    isBookSpecific = state.isBookSpecific,
                    onScopeChange = viewModel::setBookSpecific
                )
            }
            item {
                Text(
                    "ⓘ  设置仅在当前设备生效",
                    color = Color(0xFF967645),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }

    if (showFontDialog) {
        FontSelectionDialog(
            settings = settings,
            onSelect = {
                viewModel.setFontType(it)
                showFontDialog = false
            },
            onImport = {
                fontImportLauncher.launch(
                    arrayOf(
                        "font/ttf",
                        "font/otf",
                        "application/x-font-ttf",
                        "application/x-font-otf",
                        "application/octet-stream"
                    )
                )
            },
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
    index < currentIndex -> "${((index + 1) * 10).coerceAtMost(100)}%"
    index == currentIndex -> "${(progress * 100).toInt()}%"
    index > 5 -> "未读"
    else -> "未读"
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
