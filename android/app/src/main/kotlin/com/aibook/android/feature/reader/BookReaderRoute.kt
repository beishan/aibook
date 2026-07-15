package com.aibook.android.feature.reader

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aibook.android.core.model.BookFormat
import com.aibook.android.core.model.LocalBook
import com.aibook.android.di.ServiceLocator
import com.aibook.android.feature.reader.pdf.PdfReaderScreen
import com.aibook.android.core.data.repository.RelocateBookResult
import com.aibook.android.feature.importer.supportedBookMimeTypes
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun BookReaderRoute(
    bookId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val bookFlow = remember(bookId) {
        ServiceLocator.get(context.applicationContext as Application).bookRepository.observeBook(bookId)
    }
    val book by bookFlow.collectAsStateWithLifecycle(initialValue = null as LocalBook?)
    var relocateMessage by remember { mutableStateOf<String?>(null) }
    val repository = remember { ServiceLocator.get(context.applicationContext as Application).bookRepository }
    val scope = rememberCoroutineScope()
    val relocateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                relocateMessage = when (val result = repository.relocateMissingBookFile(bookId, uri)) {
                    is RelocateBookResult.Success -> null
                    is RelocateBookResult.Failure -> result.message
                }
            }
        }
    }

    val currentBook = book
    if (currentBook != null && !File(currentBook.uri).isFile) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("书籍文件已丢失")
            relocateMessage?.let { Text(it) }
            Button(onClick = { relocateLauncher.launch(supportedBookMimeTypes) }) { Text("重新定位文件") }
        }
        return
    }

    when (currentBook?.format) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        BookFormat.PDF -> PdfReaderScreen(bookId = bookId, onBack = onBack)
        else -> ReaderScreen(bookId = bookId, isRemote = false, onBack = onBack)
    }
}
