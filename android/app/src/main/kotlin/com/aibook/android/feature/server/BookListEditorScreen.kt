package com.aibook.android.feature.server

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    LaunchedEffect(listId) { viewModel.load(listId) }
    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) onDone()
    }

    DesignPage(
        title = if (listId == null) "新建书单" else "编辑书单",
        modifier = Modifier.fillMaxSize(),
        actions = {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                modifier = Modifier.clickable(onClick = onBack)
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            SoftCard {
                Text("书单信息", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = { Text("书单名称") },
                    placeholder = { Text("例如：年度科幻精选") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::setDescription,
                    label = { Text("简介") },
                    placeholder = { Text("记录这个书单的主题或阅读计划") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 4.dp))
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { viewModel.save(listId) },
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text(if (state.loading) "处理中…" else "保存书单", fontWeight = FontWeight.Bold) }
            if (listId != null) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("删除书单", color = DesignTokens.Danger) }
            }
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
