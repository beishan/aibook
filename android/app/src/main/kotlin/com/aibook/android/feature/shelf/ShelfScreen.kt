package com.aibook.android.feature.shelf

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aibook.android.core.model.LocalBook

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfScreen(
    onBookClick: (String) -> Unit,
    viewModel: ShelfViewModel = viewModel(factory = ShelfViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "imported-book.txt"
        viewModel.importBook(uri, fileName)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("我的书架", style = MaterialTheme.typography.headlineMedium)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = state.query,
                onValueChange = viewModel::setQuery,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
                singleLine = true,
                label = { Text("搜索书名或作者") }
            )
            Button(
                onClick = {
                    picker.launch(
                        arrayOf(
                            "application/epub+zip",
                            "text/plain",
                            "application/pdf",
                            "text/markdown",
                            "text/html"
                        )
                    )
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("导入")
            }
        }

        AssistChip(onClick = {}, label = { Text(state.importMessage) })

        if (state.isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val visibleBooks = state.filteredBooks

            if (visibleBooks.isEmpty() && !state.isLoading) {
                item { EmptyShelfCard() }
            }

            items(visibleBooks, key = { it.id }) { book ->
                BookCard(book = book, onClick = { onBookClick(book.id) })
            }
        }
    }
}

@Composable
private fun EmptyShelfCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("还没有本地书籍", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text("点击导入，从手机本地选择电子书。")
        }
    }
}

@Composable
private fun BookCard(book: LocalBook, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(book.title, style = MaterialTheme.typography.titleLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(book.format.displayName, style = MaterialTheme.typography.labelLarge)
                Text("阅读进度 ${(book.progress.percent * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
