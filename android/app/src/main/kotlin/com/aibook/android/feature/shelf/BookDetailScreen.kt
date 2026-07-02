package com.aibook.android.feature.shelf

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aibook.android.core.model.LocalBook
import com.aibook.android.di.ServiceLocator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    bookId: String,
    onReadClick: () -> Unit,
    onBack: () -> Unit,
    viewModel: ShelfViewModel = viewModel(factory = ShelfViewModel.Factory)
) {
    val context = LocalContext.current
    val book by produceState<LocalBook?>(initialValue = null, bookId) {
        value = ServiceLocator.get(context.applicationContext as Application).bookRepository.getBook(bookId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(book?.title ?: "书籍详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        val currentBook = book
        if (currentBook == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Text("加载中…")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(currentBook.title, style = MaterialTheme.typography.headlineMedium)
                Text(currentBook.author ?: "未知作者", style = MaterialTheme.typography.titleMedium)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AssistChip(onClick = {}, label = { Text(currentBook.format.displayName) })
                    AssistChip(onClick = {}, label = { Text("进度 ${(currentBook.progress.percent * 100).toInt()}%") })
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("阅读状态", style = MaterialTheme.typography.titleSmall)
                        Text(currentBook.status.name)
                    }
                }

                Button(
                    onClick = onReadClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.MenuBook, contentDescription = null)
                    Text("开始阅读")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.setFavorite(currentBook.id, !currentBook.favorite) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            if (currentBook.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null
                        )
                        Text(if (currentBook.favorite) "已收藏" else "收藏")
                    }
                    Button(
                        onClick = {
                            viewModel.deleteBook(currentBook.id)
                            onBack()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Text("删除")
                    }
                }
            }
        }
    }
}
