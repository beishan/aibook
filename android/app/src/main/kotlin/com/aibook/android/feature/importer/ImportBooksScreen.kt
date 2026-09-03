package com.aibook.android.feature.importer

import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aibook.android.ui.design.DesignTokens
import com.aibook.android.ui.design.SlidingSegmentedControl
import com.aibook.android.ui.design.SoftCard

@Composable
fun ImportBooksScreen(
    onBack: () -> Unit,
    onFolderImport: () -> Unit,
    viewModel: LocalBookImportViewModel = viewModel(factory = LocalBookImportViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val picker = rememberLocalBookImportLauncher { selectedUris = it }
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(DesignTokens.PagePadding),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Space16)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Text("导入书籍", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        SlidingSegmentedControl(
            options = listOf("文件", "文件夹"),
            selectedIndex = 0,
            onSelected = { if (it == 1) onFolderImport() }
        )
        SoftCard(modifier = Modifier.clickable { picker.launch(supportedBookMimeTypes) }, color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space12)) {
                Icon(Icons.Default.FolderOpen, null, tint = DesignTokens.Accent, modifier = Modifier.size(30.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (selectedUris.isEmpty()) "选择书籍文件" else "重新选择", fontWeight = FontWeight.Bold)
                    Text("支持 EPUB、TXT、MOBI、PDF、AZW3", color = DesignTokens.SoftText)
                }
                Text("浏览 ›", color = DesignTokens.Accent)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("已选择 ${selectedUris.size} 项", fontWeight = FontWeight.SemiBold)
            if (state.message.isNotBlank()) {
                Text(
                    state.message,
                    color = DesignTokens.SoftText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DesignTokens.Space8)) {
            items(selectedUris, key = { it.toString() }) { uri ->
                val info = remember(uri) { queryFileInfo(context, uri) }
                SoftCard(contentPadding = DesignTokens.Space12) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space12)) {
                        Icon(Icons.Default.Description, null, tint = DesignTokens.Accent, modifier = Modifier.size(40.dp))
                        Column(Modifier.weight(1f)) {
                            Text(info.first, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(info.second, color = DesignTokens.SoftText, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.CheckCircle, null, tint = DesignTokens.Accent)
                    }
                }
            }
        }
        Button(
            onClick = { viewModel.importBooks(selectedUris) },
            enabled = selectedUris.isNotEmpty() && !state.isImporting,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Accent),
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp)
        ) { Text(if (state.isImporting) "正在导入…" else "导入") }
    }
}

private fun queryFileInfo(context: android.content.Context, uri: Uri): Pair<String, String> {
    var name = uri.lastPathSegment ?: "书籍文件"
    var size = 0L
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
        }
    }
    val type = name.substringAfterLast('.', "FILE").uppercase()
    val sizeLabel = if (size > 1024 * 1024) String.format("%.1f MB", size / 1024.0 / 1024.0) else "${size / 1024} KB"
    return name to "$type · $sizeLabel"
}
