package com.aibook.android.core.data.repository

import android.content.Context
import android.net.Uri
import com.aibook.android.core.data.db.BookDao
import com.aibook.android.core.data.mapper.toDomain
import com.aibook.android.core.data.mapper.toEntity
import com.aibook.android.core.model.BookFormat
import com.aibook.android.core.model.ImportPolicy
import com.aibook.android.core.model.LocalBook
import com.aibook.android.core.model.ReadingProgress
import com.aibook.android.core.model.ReadingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

sealed interface ImportResult {
    data class Added(val book: LocalBook) : ImportResult
    data class Duplicate(val existingBook: LocalBook) : ImportResult
    data class UnsupportedFormat(val fileName: String) : ImportResult
    data class Failed(val message: String) : ImportResult
}

class BookRepository(
    private val context: Context,
    private val bookDao: BookDao
) {
    private val booksDir: File by lazy {
        File(context.filesDir, "books").apply { mkdirs() }
    }

    fun observeBooks(): Flow<List<LocalBook>> {
        return bookDao.observeAll().map { entities -> entities.map { it.toDomain() } }
    }

    suspend fun getBook(id: String): LocalBook? {
        return bookDao.getById(id)?.toDomain()
    }

    suspend fun importBook(uri: Uri, fileName: String): ImportResult {
        val format = BookFormat.fromFileName(fileName)
            ?: return ImportResult.UnsupportedFormat(fileName)

        val title = ImportPolicy.normalizedTitle(fileName)
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: return ImportResult.Failed("无法读取文件")

        val destFile = File(booksDir, "${UUID.randomUUID()}.$format")
        val sha256 = inputStream.use { input ->
            destFile.outputStream().use { output ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                    output.write(buffer, 0, bytesRead)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        }

        val existing = bookDao.getBySha256(sha256)
        if (existing != null) {
            destFile.delete()
            return ImportResult.Duplicate(existing.toDomain())
        }

        val book = LocalBook(
            id = UUID.nameUUIDFromBytes(sha256.toByteArray()).toString(),
            title = title,
            format = format,
            uri = destFile.absolutePath,
            sha256 = sha256,
            importedAt = Instant.now()
        )

        bookDao.insert(book.toEntity())
        return ImportResult.Added(book)
    }

    suspend fun updateProgress(
        bookId: String,
        chapterHref: String?,
        chapterTitle: String?,
        percent: Float
    ) {
        val clamped = percent.coerceIn(0f, 1f)
        val status = if (clamped >= 1f) ReadingStatus.FINISHED else ReadingStatus.READING
        bookDao.updateProgress(
            id = bookId,
            status = status.name,
            lastReadAt = System.currentTimeMillis(),
            percent = clamped,
            chapterHref = chapterHref,
            chapterTitle = chapterTitle,
            positionLabel = "${(clamped * 100).toInt()}%"
        )
    }

    suspend fun setFavorite(id: String, favorite: Boolean) {
        bookDao.setFavorite(id, favorite)
    }

    suspend fun deleteBook(id: String) {
        val book = bookDao.getById(id)
        if (book != null) {
            val file = File(book.uri)
            if (file.exists()) file.delete()
            bookDao.deleteById(id)
        }
    }

    suspend fun count(): Int = bookDao.count()
}
