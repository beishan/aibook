package com.aibook.android.feature.reader

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aibook.android.core.model.BookFormat
import com.aibook.android.core.model.LocalBook
import com.aibook.android.di.ServiceLocator
import com.aibook.android.feature.reader.pdf.PdfReaderScreen

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

    when (book?.format) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        BookFormat.PDF -> PdfReaderScreen(bookId = bookId, onBack = onBack)
        else -> ReaderScreen(bookId = bookId, isRemote = false, onBack = onBack)
    }
}
