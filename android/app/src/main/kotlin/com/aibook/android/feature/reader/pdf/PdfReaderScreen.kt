package com.aibook.android.feature.reader.pdf

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aibook.android.core.reader.BookContentError
import com.aibook.android.feature.reader.BookContentErrorText
import com.aibook.android.ui.design.DesignTokens
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun PdfReaderScreen(
    bookId: String,
    onBack: () -> Unit,
    viewModel: PdfReaderViewModel = viewModel(factory = PdfReaderViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pages by viewModel.renderedPages.collectAsStateWithLifecycle()
    val pageErrors by viewModel.pageErrors.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val pagePanOffsets = remember(bookId) { mutableStateMapOf<Int, Float>() }

    LaunchedEffect(bookId) { viewModel.open(bookId) }
    DisposableEffect(Unit) { onDispose(viewModel::close) }
    BackHandler { onBack() }

    LaunchedEffect(state.pageCount) {
        if (state.pageCount > 0) {
            val target = viewModel.navigationTarget(state.currentPage)
            listState.scrollToItem(target.pageIndex, target.scrollOffset)
        }
    }

    LaunchedEffect(listState, state.pageCount) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (page, offset) ->
                if (state.pageCount > 0) viewModel.onPageVisible(page, offset)
            }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        state.book?.title ?: "PDF",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )
                    if (state.pageCount > 0) Text("第 ${state.currentPage + 1} / ${state.pageCount} 页")
                }
                IconButton(onClick = viewModel::toggleBookmark, enabled = state.book != null) {
                    val marked = bookmarks.any { it.chapterIndex == state.currentPage }
                    Icon(
                        if (marked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = if (marked) "取消书签" else "添加书签",
                        tint = if (marked) DesignTokens.Accent else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        bottomBar = {
            if (state.pageCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = {
                            val target = viewModel.navigationTarget(state.currentPage - 1)
                            scope.launch { listState.animateScrollToItem(target.pageIndex, target.scrollOffset) }
                        },
                        enabled = state.currentPage > 0
                    ) { Icon(Icons.Default.ChevronLeft, contentDescription = "上一页") }
                    Text("${state.currentPage + 1}")
                    Slider(
                        modifier = Modifier.weight(1f),
                        value = state.currentPage.toFloat(),
                        onValueChange = { value ->
                            val target = viewModel.navigationTarget(value.toInt())
                            scope.launch { listState.scrollToItem(target.pageIndex, target.scrollOffset) }
                        },
                        valueRange = 0f..(state.pageCount - 1).coerceAtLeast(0).toFloat(),
                        steps = (state.pageCount - 2).coerceAtLeast(0)
                    )
                    Text("${state.pageCount}")
                    IconButton(
                        onClick = {
                            val target = viewModel.navigationTarget(state.currentPage + 1)
                            scope.launch { listState.animateScrollToItem(target.pageIndex, target.scrollOffset) }
                        },
                        enabled = state.currentPage < state.pageCount - 1
                    ) { Icon(Icons.Default.ChevronRight, contentDescription = "下一页") }
                }
            }
        }
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DesignTokens.Accent)
            }
            state.error != null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(pdfErrorText(state.error!!), modifier = Modifier.padding(28.dp))
            }
            else -> BoxWithConstraints(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surfaceVariant)) {
                val viewportWidthPx = with(density) { maxWidth.toPx().toInt().coerceAtLeast(1) }
                val targetWidthPx = PdfRenderSizing.targetWidthForZoom(viewportWidthPx, state.zoom)
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.pageCount, key = { it }) { pageIndex ->
                        val shouldRender by remember(pageIndex, state.currentPage) {
                            derivedStateOf { PdfRenderWindow.contains(pageIndex, state.currentPage) }
                        }
                        if (shouldRender) {
                            LaunchedEffect(pageIndex, targetWidthPx) {
                                viewModel.requestPage(pageIndex, targetWidthPx)
                            }
                        }
                        val bitmap = pages[pageIndex]
                        val displayHeight = bitmap?.let {
                            with(density) {
                                PdfRenderSizing.displayHeightFor(
                                    bitmapWidth = it.width,
                                    bitmapHeight = it.height,
                                    viewportWidth = viewportWidthPx,
                                    zoom = state.zoom
                                ).toDp()
                            }
                        }
                        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                            val requestedZoom = (state.zoom * zoomChange).coerceIn(1f, 4f)
                            viewModel.setZoom(requestedZoom)
                            val maxHorizontalPan = viewportWidthPx * (requestedZoom - 1f) / 2f
                            pagePanOffsets[pageIndex] = if (requestedZoom <= 1.01f) {
                                0f
                            } else {
                                ((pagePanOffsets[pageIndex] ?: 0f) + panChange.x)
                                    .coerceIn(-maxHorizontalPan, maxHorizontalPan)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    displayHeight?.let { Modifier.height(it) }
                                        ?: Modifier.heightIn(min = 240.dp)
                                )
                                .transformable(transformState),
                            contentAlignment = Alignment.Center
                        ) {
                            if (bitmap == null) {
                                val pageError = pageErrors[pageIndex]
                                if (pageError == null) {
                                    CircularProgressIndicator(color = DesignTokens.Accent)
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(pdfErrorText(pageError))
                                        TextButton(onClick = { viewModel.requestPage(pageIndex, targetWidthPx) }) {
                                            Text("重试")
                                        }
                                    }
                                }
                            } else {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "第 ${pageIndex + 1} 页",
                                    modifier = Modifier.fillMaxWidth().graphicsLayer(
                                        scaleX = state.zoom,
                                        scaleY = state.zoom,
                                        translationX = pagePanOffsets[pageIndex] ?: 0f
                                    ),
                                    contentScale = ContentScale.FillWidth
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun pdfErrorText(error: BookContentError): String = when (error) {
    BookContentError.FileMissing -> "PDF 文件不存在，请重新导入"
    BookContentError.PermissionLost -> "PDF 文件权限已失效，请重新导入"
    BookContentError.PasswordProtected -> "此 PDF 受密码保护，当前仅支持未加密文件"
    BookContentError.DrmProtected -> "此 PDF 包含 DRM，当前无法读取"
    BookContentError.UnsupportedVariant -> "暂不支持该 PDF 类型"
    BookContentError.CorruptedFile -> "PDF 文件损坏或无法解析"
    BookContentError.InsufficientStorage -> "内存或存储空间不足，无法渲染 PDF"
    is BookContentError.ParseFailed -> BookContentErrorText.forError(error)
}
