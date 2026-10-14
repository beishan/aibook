package com.aibook.android.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.aibook.android.core.model.LocalBook
import com.aibook.android.di.ServiceLocator
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Composable
fun RemoteBookReaderRoute(bookId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val source by produceState<RemoteReaderSource?>(initialValue = null, bookId) {
        value = null
        value = withContext(Dispatchers.IO) {
            val books = ServiceLocator.get(context).bookRepository.observeBooks().first()
            resolveRemoteReaderSource(bookId, books)
        }
    }

    when (val current = source) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is RemoteReaderSource.Downloaded -> BookReaderRoute(current.localBookId, onBack)
        RemoteReaderSource.Cloud -> ReaderScreen(
            bookId = bookId.toString(),
            isRemote = true,
            onBack = onBack
        )
    }
}

internal sealed interface RemoteReaderSource {
    data class Downloaded(val localBookId: String) : RemoteReaderSource
    data object Cloud : RemoteReaderSource
}

internal fun resolveRemoteReaderSource(bookId: Long, books: List<LocalBook>): RemoteReaderSource {
    val downloaded = books.firstOrNull { book ->
        book.remoteBookId == bookId && File(book.uri).let {
            it.isFile && it.canRead() && it.length() > 0L
        }
    }
    return downloaded?.let { RemoteReaderSource.Downloaded(it.id) } ?: RemoteReaderSource.Cloud
}
