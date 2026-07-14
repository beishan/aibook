package com.aibook.android.core.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
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
import com.aibook.android.core.reader.EpubMetadata
import com.aibook.android.core.reader.EpubMetadataParser
import com.aibook.android.core.reader.MarkdownResourceReferences
import com.aibook.android.core.mobi.MobiParseResult
import com.aibook.android.core.mobi.NativeMobiDocumentParser
import java.io.InputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
    private val shelfFolderDao: ShelfFolderDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val parsedBookStorage = ParsedBookStorage(context.filesDir)
    private val markdownCompanionStorage = MarkdownCompanionStorage(ioDispatcher)

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

    fun parsedBookDirectory(bookId: String): File = parsedBookStorage.directoryFor(bookId)

    fun observeBook(id: String): Flow<LocalBook?> {
        return bookDao.observeById(id).map { it?.toDomain() }
    }

    suspend fun importSelectedBooks(uris: List<Uri>): List<ImportResult> = withContext(ioDispatcher) {
        val selected = uris.mapNotNull { uri ->
            val displayName = resolveDisplayName(uri) ?: return@mapNotNull null
            SelectedDocument(
                value = uri,
                documentId = if (DocumentsContract.isDocumentUri(context, uri)) {
                    runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
                } else {
                    null
                },
                displayName = displayName,
                providerId = uri.authority
            )
        }
        val resources = AuthorizedMarkdownResourceIndex(selected)
        selected.mapNotNull { document ->
            val format = BookFormat.fromFileName(document.displayName) ?: return@mapNotNull null
            if (format == BookFormat.MARKDOWN) {
                importBook(document.value, document.displayName) { relativePath ->
                    resources.withResolved(document, relativePath, context.contentResolver::openInputStream)
                }
            } else {
                importBook(document.value, document.displayName)
            }
        }
    }

    suspend fun importBook(
        uri: Uri,
        fileName: String? = null,
        markdownResourceOpener: ((String) -> InputStream?)? = null
    ): ImportResult = withContext(ioDispatcher) {
        val resolvedFileName = fileName?.takeIf { it.isNotBlank() }
            ?: resolveDisplayName(uri)
            ?: "imported-book"
        val format = BookFormat.fromFileName(resolvedFileName)
            ?: return@withContext ImportResult.UnsupportedFormat(resolvedFileName)

        val inputStream = context.contentResolver.openInputStream(uri)
            ?: return@withContext ImportResult.Failed("无法读取文件")

        val destFile = createDestinationFile(format)
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
            deleteImportedFile(destFile)
            if (!existing.visibleInStore) {
                bookDao.restoreStoreVisibility(existing.id)
                return@withContext ImportResult.Restored(existing.copy(visibleInStore = true).toDomain())
            }
            return@withContext ImportResult.Duplicate(existing.toDomain())
        }

        val bookId = UUID.nameUUIDFromBytes(sha256.toByteArray()).toString()
        val metadataBytes = if (format == BookFormat.EPUB || format == BookFormat.MARKDOWN) destFile.readBytes() else byteArrayOf()
        if (format == BookFormat.MARKDOWN) {
            val references = MarkdownResourceReferences.extract(metadataBytes.toString(Charsets.UTF_8))
            markdownCompanionStorage.copy(destFile, references) { relativePath ->
                markdownResourceOpener?.invoke(relativePath)
            }
        }
        val metadata = parseMetadataSafely(format, metadataBytes, destFile, bookId)
        val title = ImportPolicy.preferredTitle(metadata?.title, resolvedFileName)
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
        ImportResult.Added(book)
    }

    suspend fun importDownloadedBook(
        fileName: String,
        bytes: ByteArray,
        fallbackTitle: String? = null
    ): ImportResult = withContext(ioDispatcher) {
        val format = BookFormat.fromFileName(fileName)
            ?: return@withContext ImportResult.UnsupportedFormat(fileName)
        val sha256 = sha256(bytes)

        val existing = bookDao.getBySha256(sha256)
        if (existing != null) {
            if (!existing.visibleInStore) {
                bookDao.restoreStoreVisibility(existing.id)
                return@withContext ImportResult.Restored(existing.copy(visibleInStore = true).toDomain())
            }
            return@withContext ImportResult.Duplicate(existing.toDomain())
        }

        val bookId = UUID.nameUUIDFromBytes(sha256.toByteArray()).toString()
        val destFile = createDestinationFile(format)
        destFile.writeBytes(bytes)
        val metadata = parseMetadataSafely(format, bytes, destFile, bookId)
        val title = ImportPolicy.preferredTitle(
            metadata?.title ?: fallbackTitle,
            fileName
        )
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
        ImportResult.Added(book)
    }

    suspend fun updateProgress(
        bookId: String,
        chapterHref: String?,
        chapterTitle: String?,
        percent: Float,
        chapterIndex: Int? = null,
        lineIndex: Int? = null,
        scrollOffset: Int = 0,
        pdfZoom: Float? = null
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
            pdfZoom = pdfZoom?.coerceIn(1f, 4f),
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
            if (file.exists()) deleteImportedFile(file)
            book.coverUri?.let { coverPath ->
                runCatching {
                    val cover = File(coverPath).canonicalFile
                    val root = coversDir.canonicalFile
                    if (cover.path.startsWith(root.path + File.separator)) cover.delete()
                }
            }
            parsedBookStorage.deleteForBook(id)
            bookDao.deleteById(id)
        }
    }

    suspend fun count(): Int = bookDao.count()

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun createDestinationFile(format: BookFormat): File {
        return if (format == BookFormat.MARKDOWN) {
            File(booksDir, "markdown-${UUID.randomUUID()}/book.md").also { it.parentFile?.mkdirs() }
        } else {
            File(booksDir, "${UUID.randomUUID()}.${format.extension}")
        }
    }

    private fun deleteImportedFile(file: File) {
        val parent = file.parentFile?.canonicalFile
        val root = booksDir.canonicalFile
        if (parent != null && parent.parentFile == root && parent.name.startsWith("markdown-")) {
            parent.deleteRecursively()
        } else {
            file.delete()
        }
    }

    private suspend fun parseMetadataSafely(
        format: BookFormat,
        bytes: ByteArray,
        sourceFile: File?,
        bookId: String
    ): EpubMetadata? = when (format) {
        BookFormat.EPUB -> runCatching { EpubMetadataParser.parse(bytes) }.getOrNull()
        BookFormat.MARKDOWN -> {
            val firstHeading = bytes.toString(Charsets.UTF_8)
                .lineSequence()
                .map(String::trim)
                .firstOrNull { it.startsWith("# ") }
                ?.removePrefix("# ")
                ?.trim()
                ?.takeIf(String::isNotBlank)
            EpubMetadata(title = firstHeading)
        }
        BookFormat.MOBI, BookFormat.AZW3 -> sourceFile?.let { file ->
            val output = File(context.cacheDir, "mobi-metadata-$bookId")
            runCatching {
                output.deleteRecursively()
                when (val result = NativeMobiDocumentParser().parse(file.path, output.path)) {
                    is MobiParseResult.Failure -> null
                    is MobiParseResult.Success -> {
                        val document = result.document
                        val coverFile = document.coverPath?.let(::File)?.takeIf(File::isFile)
                        EpubMetadata(
                            title = document.title,
                            author = document.author,
                            description = document.description,
                            coverImage = coverFile?.let {
                                EpubImage(
                                    href = it.name,
                                    mediaType = when (it.extension.lowercase()) {
                                        "png" -> "image/png"
                                        "gif" -> "image/gif"
                                        "bmp" -> "image/bmp"
                                        else -> "image/jpeg"
                                    },
                                    bytes = it.readBytes()
                                )
                            }
                        )
                    }
                }
            }.getOrNull().also { output.deleteRecursively() }
        }
        else -> null
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
