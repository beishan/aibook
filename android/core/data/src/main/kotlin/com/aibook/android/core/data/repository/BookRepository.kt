package com.aibook.android.core.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.aibook.android.core.data.db.BookDao
import com.aibook.android.core.data.db.ShelfFolderDao
import com.aibook.android.core.data.mapper.toDomain
import com.aibook.android.core.data.mapper.toEntity
import com.aibook.android.core.model.BookFormat
import com.aibook.android.core.model.ImportPolicy
import com.aibook.android.core.model.LocalBook
import com.aibook.android.core.model.ReadingProgress
import com.aibook.android.core.model.ReadingStatus
import com.aibook.android.core.model.ShelfFolder
import com.aibook.android.core.reader.EpubImage
import com.aibook.android.core.reader.EpubMetadataParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

sealed interface ImportResult {
    data class Added(val book: LocalBook) : ImportResult
    data class Restored(val book: LocalBook) : ImportResult
    data class Duplicate(val existingBook: LocalBook) : ImportResult
    data class UnsupportedFormat(val fileName: String) : ImportResult
    data class Failed(val message: String) : ImportResult
}

class BookRepository(
    private val context: Context,
    private val bookDao: BookDao,
    private val shelfFolderDao: ShelfFolderDao
) {
    suspend fun addReadingDuration(bookId: String, seconds: Long) {
        if (seconds > 0) bookDao.addReadingDuration(bookId, seconds)
    }
    private val booksDir: File by lazy {
        File(context.filesDir, "books").apply { mkdirs() }
    }
    private val coversDir: File by lazy {
        File(context.filesDir, "covers").apply { mkdirs() }
    }

    fun observeBooks(): Flow<List<LocalBook>> {
        return bookDao.observeAll().map { entities -> entities.map { it.toDomain() } }
    }

    fun observeShelvedBooks(): Flow<List<LocalBook>> {
        return bookDao.observeShelved().map { entities -> entities.map { it.toDomain() } }
    }

    fun observeShelfFolders(): Flow<List<ShelfFolder>> {
        return shelfFolderDao.observeAll().map { entities -> entities.map { it.toDomain() } }
    }

    suspend fun getBook(id: String): LocalBook? {
        return bookDao.getById(id)?.toDomain()
    }

    fun observeBook(id: String): Flow<LocalBook?> {
        return bookDao.observeById(id).map { it?.toDomain() }
    }

    suspend fun importBook(uri: Uri, fileName: String? = null): ImportResult {
        val resolvedFileName = fileName?.takeIf { it.isNotBlank() }
            ?: resolveDisplayName(uri)
            ?: "imported-book"
        val format = BookFormat.fromFileName(resolvedFileName)
            ?: return ImportResult.UnsupportedFormat(resolvedFileName)

        val inputStream = context.contentResolver.openInputStream(uri)
            ?: return ImportResult.Failed("无法读取文件")

        val destFile = File(booksDir, "${UUID.randomUUID()}.${format.extension}")
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
            if (!existing.visibleInStore) {
                bookDao.restoreStoreVisibility(existing.id)
                return ImportResult.Restored(existing.copy(visibleInStore = true).toDomain())
            }
            return ImportResult.Duplicate(existing.toDomain())
        }

        val metadata = parseMetadataSafely(format, destFile.readBytes())
        val title = metadata?.title ?: ImportPolicy.normalizedTitle(resolvedFileName)
        val bookId = UUID.nameUUIDFromBytes(sha256.toByteArray()).toString()
        val coverUri = metadata?.coverImage?.let { image -> runCatching { writeCoverImage(bookId, image) }.getOrNull() }

        val book = LocalBook(
            id = bookId,
            title = title,
            author = metadata?.author,
            description = metadata?.description,
            format = format,
            uri = destFile.absolutePath,
            sha256 = sha256,
            coverUri = coverUri,
            importedAt = Instant.now()
        )

        bookDao.insert(book.toEntity())
        return ImportResult.Added(book)
    }

    suspend fun importDownloadedBook(fileName: String, bytes: ByteArray, fallbackTitle: String? = null): ImportResult {
        val format = BookFormat.fromFileName(fileName)
            ?: return ImportResult.UnsupportedFormat(fileName)
        val sha256 = sha256(bytes)

        val existing = bookDao.getBySha256(sha256)
        if (existing != null) {
            if (!existing.visibleInStore) {
                bookDao.restoreStoreVisibility(existing.id)
                return ImportResult.Restored(existing.copy(visibleInStore = true).toDomain())
            }
            return ImportResult.Duplicate(existing.toDomain())
        }

        val metadata = parseMetadataSafely(format, bytes)
        val title = metadata?.title
            ?: fallbackTitle?.takeIf { it.isNotBlank() }
            ?: ImportPolicy.normalizedTitle(fileName)
        val destFile = File(booksDir, "${UUID.randomUUID()}.${format.extension}")
        destFile.writeBytes(bytes)
        val bookId = UUID.nameUUIDFromBytes(sha256.toByteArray()).toString()
        val coverUri = metadata?.coverImage?.let { image -> runCatching { writeCoverImage(bookId, image) }.getOrNull() }

        val book = LocalBook(
            id = bookId,
            title = title,
            author = metadata?.author,
            description = metadata?.description,
            format = format,
            uri = destFile.absolutePath,
            sha256 = sha256,
            coverUri = coverUri,
            importedAt = Instant.now()
        )

        bookDao.insert(book.toEntity())
        return ImportResult.Added(book)
    }

    suspend fun updateProgress(
        bookId: String,
        chapterHref: String?,
        chapterTitle: String?,
        percent: Float,
        chapterIndex: Int? = null,
        lineIndex: Int? = null,
        scrollOffset: Int = 0
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
            chapterIndex = chapterIndex,
            lineIndex = lineIndex,
            scrollOffset = scrollOffset.coerceAtLeast(0),
            positionLabel = "${(clamped * 100).toInt()}%"
        )
    }

    suspend fun setFavorite(id: String, favorite: Boolean) {
        bookDao.setFavorite(id, favorite)
    }

    suspend fun setShelved(id: String, shelved: Boolean) {
        bookDao.setShelved(id, shelved)
    }

    suspend fun removeFromStore(id: String) {
        bookDao.removeFromStore(id)
    }

    suspend fun createShelfFolder(name: String): ShelfFolder {
        val folder = ShelfFolder(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            createdAtEpochMillis = System.currentTimeMillis()
        )
        shelfFolderDao.insert(folder.toEntity())
        return folder
    }

    suspend fun moveBooksToFolder(ids: Collection<String>, folderId: String?) {
        if (ids.isEmpty()) return
        bookDao.setFolder(ids.toList(), folderId)
    }

    suspend fun deleteShelfFolder(id: String) {
        bookDao.clearFolder(id)
        shelfFolderDao.deleteById(id)
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

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun parseMetadataSafely(format: BookFormat, bytes: ByteArray) =
        if (format == BookFormat.EPUB) {
            runCatching { EpubMetadataParser.parse(bytes) }.getOrNull()
        } else {
            null
        }

    private fun writeCoverImage(bookId: String, image: EpubImage): String? {
        if (image.bytes.isEmpty()) return null
        val extension = when (image.mediaType.lowercase()) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            else -> image.href.substringAfterLast('.', missingDelimiterValue = "jpg").lowercase()
        }.takeIf { it.matches(Regex("[a-z0-9]+")) } ?: "jpg"
        val file = File(coversDir, "$bookId.$extension")
        file.writeBytes(image.bytes)
        return file.absolutePath
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
        }.getOrNull()
            ?: uri.lastPathSegment?.substringAfterLast('/')
    }
}
