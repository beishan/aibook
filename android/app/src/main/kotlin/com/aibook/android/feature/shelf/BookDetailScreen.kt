package com.aibook.android.feature.shelf

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aibook.android.core.model.BookFormat
import com.aibook.android.core.model.LocalBook
import com.aibook.android.core.reader.EpubContentParser
import com.aibook.android.core.reader.TextChapterParser
import com.aibook.android.di.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import java.time.Instant
import com.aibook.android.ui.design.BookCover
import com.aibook.android.ui.design.DesignTokens
import com.aibook.android.ui.design.SectionHeader
import com.aibook.android.ui.design.SoftCard
import com.aibook.android.ui.design.SourceBadge

@Composable
fun BookDetailScreen(
    bookId: String,
    onReadClick: () -> Unit,
    onBack: () -> Unit,
    viewModel: ShelfViewModel = viewModel(factory = ShelfViewModel.Factory)
) {
    val context = LocalContext.current
    val bookFlow = remember(bookId) {
        ServiceLocator.get(context.applicationContext as Application).bookRepository.observeBook(bookId)
    }
    val book by bookFlow.collectAsStateWithLifecycle(initialValue = null as LocalBook?)
    val currentBook = book

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Row {
                IconButton(onClick = {}) { Icon(Icons.Default.IosShare, contentDescription = "分享") }
                IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, contentDescription = "更多") }
            }
        }

        if (currentBook == null) {
            Text("加载中…")
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(
                title = currentBook.title,
                width = 142.dp,
                height = 212.dp,
                imageUri = currentBook.coverUri
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(currentBook.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(currentBook.author ?: "未知作者 ›", color = DesignTokens.SoftText)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SourceBadge("本地")
                    SourceBadge(currentBook.format.displayName)
                }
                Text("★ 9.2", color = DesignTokens.Accent, style = MaterialTheme.typography.headlineSmall)
                Text("1286人评分 ›", color = DesignTokens.SoftText)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("玄幻", "东方玄幻", "修真", "成长").forEach { tag -> SourceBadge(tag) }
            Text("更多 ›", color = DesignTokens.SoftText, modifier = Modifier.align(Alignment.CenterVertically))
        }

        val chapterTitles by produceState<List<String>>(emptyList(), currentBook.id, currentBook.description) {
            if (currentBook.description.isNullOrBlank()) {
                value = withContext(Dispatchers.IO) { loadChapterTitles(currentBook) }
            } else {
                value = emptyList()
            }
        }

        SoftCard {
            SectionHeader("书籍简介", "展开⌄")
            Text(
                text = currentBook.title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            val desc = currentBook.description
            if (!desc.isNullOrBlank()) {
                Text(
                    text = desc,
                    color = DesignTokens.SoftText,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                )
            } else if (chapterTitles.isNotEmpty()) {
                chapterTitles.take(10).forEachIndexed { index, title ->
                    Text(
                        text = "${index + 1}. $title",
                        color = DesignTokens.SoftText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (chapterTitles.size > 10) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "共 ${chapterTitles.size} 章 ›",
                        color = DesignTokens.SoftText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                Text(
                    text = "暂无简介",
                    color = DesignTokens.SoftText,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                )
            }
        }

        SoftCard {
            SectionHeader("书籍信息")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                InfoItem("文件格式", currentBook.format.displayName)
                InfoItem("阅读进度", "${(currentBook.progress.percent * 100).toInt()}%")
                InfoItem("累计阅读", readingDurationLabel(currentBook.readingDurationSeconds))
            }
            Spacer(Modifier.height(10.dp))
            Text("最近阅读：${lastReadLabel(currentBook.lastReadAt)}", color = DesignTokens.SoftText)
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = onReadClick,
                modifier = Modifier.weight(1f).size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = DesignTokens.Accent,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "开始阅读")
            }
            OutlinedIconButton(
                onClick = { viewModel.toggleShelved(currentBook.id, !currentBook.shelved) },
                modifier = Modifier.weight(1f).size(56.dp),
                colors = IconButtonDefaults.outlinedIconButtonColors(
                    containerColor = if (currentBook.shelved) DesignTokens.Accent else Color.Transparent,
                    contentColor = if (currentBook.shelved) Color.White else DesignTokens.Accent
                )
            ) {
                Icon(
                    if (currentBook.shelved) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = if (currentBook.shelved) "移除书架" else "加入书架",
                    tint = if (currentBook.shelved) Color.White else DesignTokens.Accent
                )
            }
            IconButton(
                onClick = { viewModel.setFavorite(currentBook.id, !currentBook.favorite) },
                modifier = Modifier.weight(1f).size(56.dp)
            ) {
                Icon(
                    if (currentBook.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "收藏",
                    tint = if (currentBook.favorite) DesignTokens.Accent else DesignTokens.SoftText
                )
            }
        }

        Button(
            onClick = {
                viewModel.deleteBook(currentBook.id)
                onBack()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Delete, contentDescription = null)
            Text("删除")
        }
    }
}

private fun readingDurationLabel(seconds: Long): String {
    val minutes = seconds.coerceAtLeast(0) / 60
    return if (minutes >= 60) "${minutes / 60}小时${minutes % 60}分" else "${minutes}分"
}

private fun lastReadLabel(lastReadAt: Instant?): String {
    if (lastReadAt == null) return "尚未开始"
    val minutes = Duration.between(lastReadAt, Instant.now()).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "$minutes 分钟前"
        minutes < 24 * 60 -> "${minutes / 60} 小时前"
        else -> "${minutes / (24 * 60)} 天前"
    }
}

@Composable
private fun InfoItem(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = DesignTokens.SoftText, style = MaterialTheme.typography.bodySmall)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

private fun loadChapterTitles(book: LocalBook): List<String> {
    return try {
        val file = File(book.uri)
        if (!file.exists()) return emptyList()
        when (book.format) {
            BookFormat.EPUB -> {
                val epub = EpubContentParser.parse(file.readBytes())
                epub.chapters.map { it.title }
            }
            BookFormat.TXT, BookFormat.MARKDOWN, BookFormat.HTML, BookFormat.HTM -> {
                val text = file.readText()
                TextChapterParser.parse(text).map { it.title }
            }
            BookFormat.MOBI, BookFormat.AZW3 -> emptyList()
            else -> emptyList()
        }
    } catch (e: Exception) {
        emptyList()
    }
}
