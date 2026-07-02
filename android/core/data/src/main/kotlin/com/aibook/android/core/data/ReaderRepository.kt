package com.aibook.android.core.data

import com.aibook.android.core.model.BookFormat
import com.aibook.android.core.model.ImportPolicy
import com.aibook.android.core.model.LocalBook
import java.util.UUID

class ReaderRepository {
    private val books = mutableListOf<LocalBook>()

    fun previewImport(fileName: String, uri: String): LocalBook? {
        val format = BookFormat.fromFileName(fileName) ?: return null

        return LocalBook(
            id = UUID.nameUUIDFromBytes(uri.toByteArray()).toString(),
            title = ImportPolicy.normalizedTitle(fileName),
            format = format,
            uri = uri
        )
    }

    fun addBook(book: LocalBook) {
        if (books.none { it.id == book.id }) {
            books += book
        }
    }

    fun addImportedBook(fileName: String, uri: String, sha256: String): ImportResult {
        val format = BookFormat.fromFileName(fileName)
            ?: return ImportResult.UnsupportedFormat(fileName)

        books.firstOrNull { it.sha256 == sha256 }?.let { existing ->
            return ImportResult.Duplicate(existing)
        }

        val book = LocalBook(
            id = UUID.nameUUIDFromBytes(sha256.toByteArray()).toString(),
            title = ImportPolicy.normalizedTitle(fileName),
            format = format,
            uri = uri,
            sha256 = sha256
        )

        books += book
        return ImportResult.Added(book)
    }

    fun listBooks(): List<LocalBook> {
        return books.sortedWith(compareByDescending<LocalBook> { it.lastReadAt }.thenBy { it.title })
    }
}

sealed interface ImportResult {
    data class Added(val book: LocalBook) : ImportResult
    data class Duplicate(val existingBook: LocalBook) : ImportResult
    data class UnsupportedFormat(val fileName: String) : ImportResult
}
