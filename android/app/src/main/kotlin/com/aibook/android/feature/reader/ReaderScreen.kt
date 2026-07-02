package com.aibook.android.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aibook.android.core.model.ReaderTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: String,
    isRemote: Boolean,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = viewModel(factory = ReaderViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = state.settings
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(bookId, isRemote) {
        if (isRemote) {
            viewModel.loadRemoteBook(bookId.toLongOrNull() ?: 0L)
        } else {
            viewModel.loadLocalBook(bookId)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveProgress()
        }
    }

    val background = when (settings.theme) {
        ReaderTheme.LIGHT -> Color(0xFFFFFFFF)
        ReaderTheme.PAPER -> Color(0xFFFAF3E0)
        ReaderTheme.DARK -> Color(0xFF151515)
    }
    val foreground = if (settings.theme == ReaderTheme.DARK) Color(0xFFE8E1D4) else Color(0xFF222222)

    val scrollState = rememberLazyListState()

    LaunchedEffect(scrollState) {
        snapshotFlow {
            val firstVisible = scrollState.firstVisibleItemIndex
            val offset = scrollState.firstVisibleItemScrollOffset
            val total = state.content.length.coerceAtLeast(1)
            val charPerItem = total / 100.coerceAtLeast(1)
            val scrolled = firstVisible * charPerItem + (offset * charPerItem / 1000)
            (scrolled.toFloat() / total).coerceIn(0f, 1f)
        }.collect { progress ->
            viewModel.updateScrollProgress(progress)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.book?.title ?: "阅读器") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = !showSettings }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(background)
        ) {
            if (showSettings) {
                ReaderSettingsPanel(
                    settings = settings,
                    onFontScaleChange = viewModel::setFontScale,
                    onLineHeightChange = viewModel::setLineHeight,
                    onThemeChange = viewModel::setTheme,
                    foreground = foreground
                )
            }

            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (state.errorMessage != null) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(state.errorMessage!!, color = foreground)
                }
            } else if (state.content.isNotBlank()) {
                val paragraphs = state.content.split("\n").filter { it.isNotBlank() }
                LazyColumn(
                    state = scrollState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 20.dp,
                        vertical = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(paragraphs.size) { index ->
                        Text(
                            text = paragraphs[index],
                            color = foreground,
                            fontSize = (18 * settings.fontScale).sp,
                            lineHeight = (28 * settings.fontScale * settings.lineHeight).sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderSettingsPanel(
    settings: com.aibook.android.core.model.ReaderSettings,
    onFontScaleChange: (Float) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onThemeChange: (ReaderTheme) -> Unit,
    foreground: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("阅读设置", style = MaterialTheme.typography.titleSmall, color = foreground)
        Text("字号 ${(settings.fontScale * 100).toInt()}%", color = foreground)
        Slider(
            value = settings.fontScale,
            onValueChange = onFontScaleChange,
            valueRange = 0.8f..1.6f
        )
        Text("行距 ${(settings.lineHeight * 100).toInt()}%", color = foreground)
        Slider(
            value = settings.lineHeight,
            onValueChange = onLineHeightChange,
            valueRange = 1.0f..2.0f
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReaderTheme.entries.forEach { theme ->
                FilterChip(
                    selected = settings.theme == theme,
                    onClick = { onThemeChange(theme) },
                    label = { Text(readerThemeLabel(theme)) }
                )
            }
        }
    }
}

private fun readerThemeLabel(theme: ReaderTheme): String = when (theme) {
    ReaderTheme.LIGHT -> "白"
    ReaderTheme.PAPER -> "纸"
    ReaderTheme.DARK -> "黑"
}
