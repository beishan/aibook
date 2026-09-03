package com.aibook.android.feature.server

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aibook.android.core.data.repository.ServerRepository
import com.aibook.android.di.ServiceLocator
import com.aibook.android.ui.design.DesignPage
import com.aibook.android.ui.design.DesignTokens
import com.aibook.android.ui.design.SoftCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BookListEditorState(
    val name: String = "",
    val description: String = "",
    val loading: Boolean = false,
    val saved: Boolean = false,
    val deleted: Boolean = false,
    val error: String? = null
)

class BookListEditorViewModel(private val repository: ServerRepository) : ViewModel() {
    private val _state = MutableStateFlow(BookListEditorState())
    val state = _state.asStateFlow()

    fun load(listId: Long?) {
        if (listId == null) return
        if (CloudMockData.enabled) {
            val list = CloudMockData.bookList(listId)
            _state.update {
                if (list == null) it.copy(loading = false, error = "Mock 书单不存在")
                else it.copy(name = list.name, description = list.description.orEmpty(), loading = false, error = null)
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            repository.getBookList(listId)
                .onSuccess { list ->
                    _state.update {
                        it.copy(name = list.name, description = list.description.orEmpty(), loading = false)
                    }
                }
                .onFailure { error -> _state.update { it.copy(loading = false, error = error.message) } }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value) }
    fun setDescription(value: String) = _state.update { it.copy(description = value) }

    fun save(listId: Long?) {
        val value = _state.value
        if (value.name.isBlank()) {
            _state.update { it.copy(error = "请输入书单名称") }
            return
        }
        if (CloudMockData.enabled) {
            _state.update { it.copy(loading = false, saved = true, error = null) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val result = if (listId == null) {
                repository.createBookList(value.name.trim(), value.description.trim())
            } else {
                repository.updateBookList(listId, value.name.trim(), value.description.trim())
            }
            result.onSuccess { _state.update { it.copy(loading = false, saved = true) } }
                .onFailure { error -> _state.update { it.copy(loading = false, error = error.message) } }
        }
    }

    fun delete(listId: Long) {
        if (CloudMockData.enabled) {
            _state.update { it.copy(loading = false, deleted = true, error = null) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            repository.deleteBookList(listId)
                .onSuccess { _state.update { it.copy(loading = false, deleted = true) } }
                .onFailure { error -> _state.update { it.copy(loading = false, error = error.message) } }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as Application
                BookListEditorViewModel(ServiceLocator.get(application).serverRepository)
            }
        }
    }
}

@Composable
fun BookListEditorScreen(
    listId: Long?,
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: BookListEditorViewModel = viewModel(factory = BookListEditorViewModel.Factory)
) {
    val state by viewModel.state.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    var syncToServer by remember { mutableStateOf(true) }
    LaunchedEffect(listId) { viewModel.load(listId) }
    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) onDone()
    }

    DesignPage(
        title = if (listId == null) "新建书单" else "编辑书单",
        centerTitle = true,
        modifier = Modifier.fillMaxSize(),
        navigation = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Space16)
        ) {
            CloudMockNotice()
            if (listId == null) {
                Text("✦ 创建你的专属书单，收藏心仪的小说", modifier = Modifier.fillMaxWidth(), color = DesignTokens.Accent, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            SoftCard {
                EditorFieldLabel(Icons.AutoMirrored.Filled.MenuBook, if (listId == null) "书单名称" else "基本信息")
                Spacer(Modifier.height(DesignTokens.Space12))
                OutlinedTextField(
                    value = state.name.take(20),
                    onValueChange = { viewModel.setName(it.take(20)) },
                    label = { Text(if (listId == null) "名称" else "书单名称") },
                    placeholder = { Text("例如：年度科幻精选") },
                    supportingText = { Text("${state.name.length.coerceAtMost(20)}/20", modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.End) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(DesignTokens.Space12))
                EditorFieldLabel(Icons.Default.Edit, "描述")
                Spacer(Modifier.height(DesignTokens.Space8))
                OutlinedTextField(
                    value = state.description.take(200),
                    onValueChange = { viewModel.setDescription(it.take(200)) },
                    label = { Text("描述") },
                    placeholder = { Text("记录这个书单的主题或阅读计划") },
                    supportingText = { Text("${state.description.length.coerceAtMost(200)}/200", modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.End) },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(DesignTokens.Space16))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space12)) {
                    EditorFieldLabel(Icons.Default.Image, "封面")
                    Text(
                        "✦ AI 自动生成",
                        modifier = Modifier.background(DesignTokens.WarmCard, RoundedCornerShape(DesignTokens.RadiusLarge)).padding(horizontal = 12.dp, vertical = 6.dp),
                        color = DesignTokens.Accent,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Spacer(Modifier.height(DesignTokens.Space12))
                BooklistCoverPlaceholder()
                if (listId == null) {
                    Spacer(Modifier.height(DesignTokens.Space16))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("同步到云端", fontWeight = FontWeight.Bold)
                            Text("创建后支持多设备访问", color = DesignTokens.SoftText)
                        }
                        Switch(checked = syncToServer, onCheckedChange = { syncToServer = it })
                    }
                }
            }
            if (listId != null) {
                SoftCard {
                    EditorActionRow("重命名", "修改书单名称", DesignTokens.Accent) {}
                    EditorActionRow("修改描述", "修改书单的描述信息", DesignTokens.Accent) {}
                    EditorActionRow("删除书单", "删除后将无法恢复，请谨慎操作", DesignTokens.Danger) { confirmDelete = true }
                }
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 4.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space12)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).height(54.dp)) { Text("取消") }
                Button(
                    onClick = { viewModel.save(listId) },
                    enabled = !state.loading,
                    modifier = Modifier.weight(1f).height(54.dp)
                ) { Text(if (state.loading) "处理中…" else if (listId == null) "创建" else "保存", fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(DesignTokens.Space16))
        }
    }

    if (confirmDelete && listId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这个书单？") },
            text = { Text("书单中的书籍不会被删除。") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete(listId) }) {
                    Text("删除", color = DesignTokens.Danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun BooklistCoverPlaceholder() {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth(0.58f).aspectRatio(1f).background(DesignTokens.WarmCard, RoundedCornerShape(DesignTokens.CardRadius)).padding(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            repeat(2) { row ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(2) { column ->
                        val colors = if ((row + column) % 2 == 0) listOf(Color(0xFF617A87), Color(0xFF263640)) else listOf(Color(0xFFC99461), Color(0xFF74452C))
                        Spacer(Modifier.weight(1f).fillMaxSize().background(Brush.verticalGradient(colors), RoundedCornerShape(DesignTokens.RadiusSmall)))
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorFieldLabel(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space8)) {
        Icon(icon, contentDescription = null, tint = DesignTokens.Accent)
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EditorActionRow(title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = DesignTokens.Space12),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, color = DesignTokens.SoftText)
        }
        Text("›", color = color, style = MaterialTheme.typography.headlineMedium)
    }
}
