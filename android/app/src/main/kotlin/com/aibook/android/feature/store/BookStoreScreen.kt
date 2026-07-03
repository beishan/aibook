package com.aibook.android.feature.store

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aibook.android.core.model.LocalBook
import java.time.Duration
import java.time.Instant
import kotlin.math.absoluteValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aibook.android.ui.design.BookCover
import com.aibook.android.ui.design.DesignPage
import com.aibook.android.ui.design.DesignTokens
import com.aibook.android.ui.design.SectionHeader
import com.aibook.android.ui.design.SoftCard
import com.aibook.android.ui.design.SourceBadge

@Composable
fun BookStoreScreen(
    onCategoryClick: () -> Unit = {},
    onBookClick: (String) -> Unit = {},
    viewModel: StoreViewModel = viewModel(factory = StoreViewModel.Factory)
) {
    val localBooks by viewModel.books.collectAsState()
    val storeBooks = remember(localBooks) { localBooks.map { it.toStoreBook() } }
    val categories = remember(storeBooks) {
        storeBooks.flatMap { it.categories }.distinct().sorted()
    }

    var selectedSource by remember { mutableStateOf(StoreSourceFilter.ALL) }
    var selectedCategory by remember { mutableStateOf("全部") }
    var query by remember { mutableStateOf("") }
    val filteredBooks by remember(storeBooks, selectedSource, selectedCategory, query) {
        derivedStateOf {
            storeBooks.filter { book ->
                val sourceMatches = selectedSource == StoreSourceFilter.ALL || book.sourceType == selectedSource
                val categoryMatches = selectedCategory == "全部" || selectedCategory in book.categories
                val queryMatches = query.isBlank() ||
                    book.title.contains(query, ignoreCase = true) ||
                    book.author.contains(query, ignoreCase = true) ||
                    book.categories.any { it.contains(query, ignoreCase = true) }
                sourceMatches && categoryMatches && queryMatches
            }
        }
    }
    val featuredBooks = remember(storeBooks) { storeBooks.take(3) }
    val recentBooks = remember(storeBooks) { storeBooks.sortedByDescending { it.updatedRank }.take(6) }

    DesignPage(
        title = "书城",
        modifier = Modifier.fillMaxSize(),
        actions = {
            Icon(Icons.Default.Search, contentDescription = "搜索")
            Icon(
                Icons.AutoMirrored.Filled.Sort,
                contentDescription = "筛选",
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCategoryClick
                )
            )
            Text(
                "筛选",
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCategoryClick
                )
            )
        }
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                StoreSourceSegment(
                    selected = selectedSource,
                    onSelect = { selectedSource = it }
                )
            }
//            item {
//                OutlinedTextField(
//                    modifier = Modifier.fillMaxWidth(),
//                    value = query,
//                    onValueChange = { query = it },
//                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
//                    placeholder = { Text("搜索书名 / 作者 / 分类") },
//                    shape = RoundedCornerShape(24.dp)
//                )
//            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        StoreChip(
                            label = "全部",
                            selected = selectedCategory == "全部",
                            onClick = { selectedCategory = "全部" }
                        )
                    }
                    items(categories) { category ->
                        StoreChip(
                            label = category,
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category }
                        )
                    }
                    item {
                        Text(
                            "查看全部",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            color = DesignTokens.SoftText
                        )
                    }
                }
            }
//            item {
//                StoreHeroCard(featuredBooks = featuredBooks, onExploreClick = onCategoryClick)
//            }
//            item { SectionHeader("全部浏览", "全部书籍 ${filteredBooks.size} ›") }
            item {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().height(620.dp)
                ) {
                    gridItems(filteredBooks) { book ->
                        StoreBookCard(book, onBookClick)
                    }
                }
            }
//            item { SectionHeader("最近更新", "更多更新 ›") }
//            item {
//                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
//                    items(recentBooks) { book ->
//                        RecentUpdateCard(book)
//                    }
//                }
//            }
        }
    }
}

@Composable
private fun StoreSourceSegment(
    selected: StoreSourceFilter,
    onSelect: (StoreSourceFilter) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8F4F0), RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        StoreSourceFilter.entries.forEach { source ->
            Text(
                text = source.label,
                color = if (source == selected) DesignTokens.Accent else Color.Black,
                fontWeight = if (source == selected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (source == selected) Color.White else Color.Transparent,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(source) }
                    .padding(vertical = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun StoreChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Text(
        label,
        color = if (selected) DesignTokens.Accent else Color.Black,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .background(
                if (selected) DesignTokens.Accent.copy(alpha = 0.08f) else Color.Transparent,
                RoundedCornerShape(18.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun StoreHeroCard(
    featuredBooks: List<StoreBook>,
    onExploreClick: () -> Unit
) {
    SoftCard(color = DesignTokens.WarmCard) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "今日推荐",
                    modifier = Modifier
                        .background(Color(0xFFFFE8D0), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    color = DesignTokens.Accent,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "每一本好书\n都是一次探索",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text("精选优质图书，发现更多精彩", color = DesignTokens.SoftText)
                Text(
                    "立即探索",
                    modifier = Modifier
                        .background(Brush.horizontalGradient(listOf(DesignTokens.Accent, DesignTokens.AccentDark)), RoundedCornerShape(22.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onExploreClick
                        )
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                modifier = Modifier.padding(top = 28.dp),
                horizontalArrangement = Arrangement.spacedBy((-22).dp)
            ) {
                featuredBooks.forEachIndexed { index, book ->
                    BookCover(
                        title = book.title,
                        width = 58.dp,
                        height = 86.dp,
                        brush = Brush.verticalGradient(listOf(book.color, Color(0xFF1C1B18))),
                        modifier = Modifier.padding(top = (index % 2 * 12).dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StoreCategoryScreen(
    onBack: () -> Unit,
    onBookClick: (String) -> Unit = {},
    viewModel: StoreViewModel = viewModel(factory = StoreViewModel.Factory)
) {
    val localBooks by viewModel.books.collectAsState()
    val categoryBooks = remember(localBooks) { localBooks.map { it.toStoreBook() } }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                "分类查找",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold
            )
            Icon(Icons.Default.Search, contentDescription = "搜索")
            Row(
                modifier = Modifier.padding(start = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Icon(Icons.Default.FilterList, contentDescription = null)
                Text("筛选", style = MaterialTheme.typography.titleMedium)
            }
        }
        FilterRow("来源", listOf("全部", "本地", "OPDS"), selected = 0)
        TextTabs(listOf("全部", "小说", "文学", "社科", "历史", "科技", "外文", "少儿"), selected = 0)
        FilterRow("排序", listOf("综合", "评分", "最新"), selected = 0)
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            gridItems(categoryBooks) { book ->
                CategoryBookCard(book, onBookClick)
            }
        }
    }
}

@Composable
private fun FilterRow(
    label: String,
    options: List<String>,
    selected: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(label, color = DesignTokens.SoftText, style = MaterialTheme.typography.titleMedium)
        options.forEachIndexed { index, option ->
            Text(
                option,
                color = if (index == selected) DesignTokens.Accent else Color(0xFF2F2B26),
                fontWeight = if (index == selected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .background(
                        if (index == selected) DesignTokens.Accent.copy(alpha = 0.08f) else Color(0xFFF9F8F7),
                        RoundedCornerShape(22.dp)
                    )
                    .border(
                        1.dp,
                        if (index == selected) DesignTokens.Accent.copy(alpha = 0.42f) else DesignTokens.Hairline,
                        RoundedCornerShape(22.dp)
                    )
                    .padding(horizontal = 22.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun TextTabs(options: List<String>, selected: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        options.forEachIndexed { index, option ->
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text(
                    option,
                    color = if (index == selected) DesignTokens.Accent else DesignTokens.SoftText,
                    fontWeight = if (index == selected) FontWeight.Bold else FontWeight.Normal
                )
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .height(4.dp)
                        .width(36.dp)
                        .background(if (index == selected) DesignTokens.Accent else Color.Transparent, RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

@Composable
private fun CategoryBookCard(book: StoreBook, onBookClick: (String) -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onBookClick(book.id) },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DesignTokens.Hairline)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box {
                BookCover(
                    title = book.title,
                    modifier = Modifier.fillMaxWidth(),
                    width = 96.dp,
                    height = 132.dp,
                    brush = Brush.verticalGradient(listOf(book.color, Color(0xFF1C1B18)))
                )
                CategorySourceBadge(
                    text = book.source,
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.BottomStart)
                        .padding(6.dp)
                )
            }
            Text(book.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(book.author, color = DesignTokens.SoftText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("★ ${book.rating}", color = DesignTokens.Accent)
        }
    }
}

@Composable
private fun CategorySourceBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .background(Color(0xFFFFF1D9), RoundedCornerShape(5.dp))
            .border(1.dp, Color(0xFFE6C99B), RoundedCornerShape(5.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = Color(0xFF62401D),
        style = MaterialTheme.typography.labelMedium
    )
}

@Composable
private fun StoreBookCard(book: StoreBook, onBookClick: (String) -> Unit = {}) {
    SoftCard(
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { onBookClick(book.id) }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            BookCover(
                title = book.title,
                width = 72.dp,
                height = 104.dp,
                brush = Brush.verticalGradient(listOf(book.color, Color(0xFF1C1B18)))
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(book.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.FilterList, contentDescription = null, tint = DesignTokens.SoftText)
                }
                Text(book.author, color = DesignTokens.SoftText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("★ ${book.rating}", color = DesignTokens.Accent)
                SourceBadge(book.source)
            }
        }
    }
}

@Composable
private fun RecentUpdateCard(book: StoreBook) {
    Card(
        modifier = Modifier.width(168.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, DesignTokens.Hairline),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BookCover(
                title = book.title,
                width = 50.dp,
                height = 70.dp,
                brush = Brush.verticalGradient(listOf(book.color, Color(0xFF1C1B18)))
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(book.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(book.author, color = DesignTokens.SoftText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(book.updatedLabel, color = DesignTokens.SoftText, style = MaterialTheme.typography.bodySmall)
                SourceBadge(book.source)
            }
        }
    }
}

private data class StoreBook(
    val id: String,
    val title: String,
    val author: String,
    val rating: String,
    val source: String,
    val sourceType: StoreSourceFilter,
    val categories: List<String>,
    val updatedLabel: String,
    val updatedRank: Int,
    val color: Color
)

private enum class StoreSourceFilter(val label: String) {
    ALL("全部"),
    LOCAL("本地"),
    OPDS("OPDS")
}

private fun LocalBook.toStoreBook(): StoreBook {
    val color = titleColor(title)
    val now = Instant.now()
    val refTime = lastReadAt ?: importedAt
    return StoreBook(
        id = id,
        title = title,
        author = author ?: "未知作者",
        rating = format.displayName,
        source = "本地",
        sourceType = StoreSourceFilter.LOCAL,
        categories = listOf(format.displayName),
        updatedLabel = relativeTime(refTime, now),
        updatedRank = refTime.epochSecond.toInt(),
        color = color
    )
}

private fun relativeTime(instant: Instant, now: Instant): String {
    val duration = Duration.between(instant, now)
    return when {
        duration.toMinutes() < 1 -> "刚刚更新"
        duration.toMinutes() < 60 -> "${duration.toMinutes()} 分钟前"
        duration.toHours() < 24 -> "${duration.toHours()} 小时前"
        duration.toDays() < 7 -> "${duration.toDays()} 天前"
        duration.toDays() < 30 -> "${duration.toDays() / 7} 周前"
        else -> "更早"
    }
}

private fun titleColor(title: String): Color {
    val colors = listOf(
        Color(0xFF253542), Color(0xFF4D171B), Color(0xFF222222),
        Color(0xFF9DBCC1), Color(0xFF6B4A2E), Color(0xFFD47A1F),
        Color(0xFF405B4B), Color(0xFF7C8FA6), Color(0xFF84613F),
        Color(0xFF1D5B76), Color(0xFF6FA8B6), Color(0xFF173B52)
    )
    return colors[title.hashCode().absoluteValue % colors.size]
}
