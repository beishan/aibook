package com.aibook.android.feature.importer

import android.app.Application
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aibook.android.core.data.repository.BookRepository
import com.aibook.android.core.data.repository.ImportResult
import com.aibook.android.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LocalBookImportState(
    val isImporting: Boolean = false,
    val message: String = "支持 EPUB、TXT、PDF、Markdown、HTML"
)

class LocalBookImportViewModel(
    private val bookRepository: BookRepository
) : ViewModel() {
    private val _state = MutableStateFlow(LocalBookImportState())
    val state: StateFlow<LocalBookImportState> = _state.asStateFlow()

    fun importBooks(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isImporting = true)
            val results = uris.map { uri -> bookRepository.importBook(uri) }
            val added = results.count { it is ImportResult.Added }
            val duplicate = results.count { it is ImportResult.Duplicate }
            val unsupported = results.filterIsInstance<ImportResult.UnsupportedFormat>()
            val failed = results.filterIsInstance<ImportResult.Failed>()

            _state.value = LocalBookImportState(
                isImporting = false,
                message = importSummary(
                    added = added,
                    duplicate = duplicate,
                    unsupported = unsupported,
                    failed = failed
                )
            )
        }
    }

    private fun importSummary(
        added: Int,
        duplicate: Int,
        unsupported: List<ImportResult.UnsupportedFormat>,
        failed: List<ImportResult.Failed>
    ): String {
        if (added == 1 && duplicate == 0 && unsupported.isEmpty() && failed.isEmpty()) {
            return "已导入 1 本书"
        }
        val parts = buildList {
            if (added > 0) add("导入 $added 本")
            if (duplicate > 0) add("重复 $duplicate 本")
            if (unsupported.isNotEmpty()) add("不支持 ${unsupported.size} 个")
            if (failed.isNotEmpty()) add("失败 ${failed.size} 个")
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("，") ?: "未导入书籍"
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                LocalBookImportViewModel(ServiceLocator.get(app).bookRepository)
            }
        }
    }
}

@Composable
fun rememberLocalBookImportLauncher(
    onSelected: (List<Uri>) -> Unit
): ManagedActivityResultLauncher<Array<String>, List<Uri>> {
    return rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        onSelected(uris)
    }
}

val supportedBookMimeTypes = arrayOf(
    "application/epub+zip",
    "text/plain",
    "application/pdf",
    "text/markdown",
    "text/html",
    "application/xhtml+xml",
    "application/octet-stream"
)
