package com.aibook.android.core.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.aibook.android.core.data.db.AiBookDatabase
import com.aibook.android.core.data.db.BookEntity
import com.aibook.android.core.data.db.ReaderBookmarkEntity
import com.aibook.android.core.data.db.ReaderHighlightEntity
import com.aibook.android.core.data.db.ShelfFolderEntity
import com.aibook.android.core.model.BookFormat
import java.io.File
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class LocalBackupSummary(
    val bookCount: Int,
    val fileCount: Int,
    val bookmarkCount: Int,
    val highlightCount: Int
)

object LocalBackupManager {
    private const val APP_NAME = "汗牛充栋"
    private const val FORMAT_VERSION = 1
    private const val MANIFEST_NAME = "manifest.json"

    suspend fun create(context: Context, destination: Uri): LocalBackupSummary = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val database = AiBookDatabase.get(appContext)
        val snapshot = database.withTransaction {
            BackupSnapshot(
                books = database.bookDao().getAll(),
                folders = database.shelfFolderDao().getAll(),
                bookmarks = database.readerBookmarkDao().getAll(),
                highlights = database.readerHighlightDao().getAll()
            )
        }
        val output = appContext.contentResolver.openOutputStream(destination)
            ?: error("无法创建备份文件")
        var fileCount = 0
        ZipOutputStream(output.buffered()).use { zip ->
            zip.setLevel(Deflater.BEST_SPEED)
            val manifest = snapshot.toManifest { index, book ->
                val fileEntry = book.uri.takeIf(String::isNotBlank)?.let { rawPath ->
                    val input = openBookInput(appContext, rawPath)
                    if (input == null) {
                        if (book.source == "SERVER") null else error("书籍文件缺失：${book.title}")
                    } else {
                        val entryName = "book-files/$index.bin"
                        zip.putNextEntry(ZipEntry(entryName))
                        input.use { it.copyTo(zip) }
                        zip.closeEntry()
                        fileCount++
                        entryName
                    }
                }
                val coverEntry = book.coverUri?.takeIf(String::isNotBlank)?.let { path ->
                    val cover = File(path)
                    if (!cover.isFile) null else {
                        val entryName = "cover-files/$index.bin"
                        zip.putNextEntry(ZipEntry(entryName))
                        cover.inputStream().buffered().use { it.copyTo(zip) }
                        zip.closeEntry()
                        fileCount++
                        entryName
                    }
                }
                val assetEntries = if (
                    book.format == BookFormat.MARKDOWN.name && fileEntry != null && File(book.uri).isFile
                ) {
                    val source = File(book.uri).canonicalFile
                    val root = source.parentFile?.canonicalFile
                    if (root == null || !root.isDirectory) emptyList() else {
                        collectFiles(root).filter { it.canonicalFile != source }.map { asset ->
                            val relativePath = asset.relativeTo(root).invariantSeparatorsPath
                            require(isSafeRelativePath(relativePath)) { "Markdown 资源路径无效：$relativePath" }
                            val entryName = "markdown-assets/$index/$relativePath"
                            zip.putNextEntry(ZipEntry(entryName))
                            asset.inputStream().buffered().use { it.copyTo(zip) }
                            zip.closeEntry()
                            fileCount++
                            entryName
                        }
                    }
                } else emptyList()
                BookArchiveEntries(fileEntry, coverEntry, assetEntries)
            }
            zip.putNextEntry(ZipEntry(MANIFEST_NAME))
            zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        LocalBackupSummary(
            bookCount = snapshot.books.size,
            fileCount = fileCount,
            bookmarkCount = snapshot.bookmarks.size,
            highlightCount = snapshot.highlights.size
        )
    }

    suspend fun restore(context: Context, source: Uri): LocalBackupSummary = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val stagedArchive = File.createTempFile("ai-book-restore-", ".zip", appContext.cacheDir)
        try {
            val input = appContext.contentResolver.openInputStream(source)
                ?: error("无法读取备份文件")
            input.use { sourceStream ->
                stagedArchive.outputStream().buffered().use { output -> sourceStream.copyTo(output) }
            }
            ZipFile(stagedArchive).use { zip ->
                val manifestEntry = zip.getEntry(MANIFEST_NAME) ?: error("备份文件缺少目录信息")
                val manifest = zip.getInputStream(manifestEntry).bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
                require(manifest.optString("app") == APP_NAME) { "不是汗牛充栋的备份文件" }
                require(manifest.optInt("formatVersion") == FORMAT_VERSION) { "备份文件版本不受支持" }

                val snapshot = manifest.toSnapshot()
                validateArchive(zip, snapshot)
                val database = AiBookDatabase.get(appContext)
                val createdFiles = mutableListOf<File>()
                try {
                    val restoredBooks = snapshot.books.mapIndexed { index, archivedBook ->
                        val entries = snapshot.fileEntries[index]
                        val current = database.bookDao().getById(archivedBook.id)
                        val restoredUri = entries.fileEntry?.let { entryName ->
                            val format = BookFormat.valueOf(archivedBook.format)
                            val parent = File(appContext.filesDir, "books/restore-${UUID.randomUUID()}")
                                .apply { mkdirs() }
                            val destination = if (format == BookFormat.MARKDOWN) {
                                File(parent, "book.${format.extension}")
                            } else {
                                File(parent, "book.${format.extension}")
                            }
                            copyZipEntry(zip, entryName, destination)
                            createdFiles += destination
                            entries.assetEntries.forEach { assetEntry ->
                                val prefix = "markdown-assets/$index/"
                                val relativePath = assetEntry.removePrefix(prefix)
                                require(assetEntry.startsWith(prefix) && isSafeRelativePath(relativePath)) {
                                    "备份中的 Markdown 资源路径无效"
                                }
                                val asset = File(parent, relativePath).canonicalFile
                                require(asset.path.startsWith(parent.canonicalPath + File.separator)) {
                                    "备份中的 Markdown 资源路径越界"
                                }
                                copyZipEntry(zip, assetEntry, asset)
                                createdFiles += asset
                            }
                            destination.absolutePath
                        } ?: if (archivedBook.source == "SERVER") {
                            current?.uri ?: ""
                        } else {
                            current?.uri?.takeIf { File(it).isFile }
                                ?: error("备份中缺少书籍文件：${archivedBook.title}")
                        }
                        val restoredCover = entries.coverEntry?.let { entryName ->
                            val coverDirectory = File(appContext.filesDir, "covers").apply { mkdirs() }
                            val cover = File(coverDirectory, "restore-${UUID.randomUUID()}.img")
                            copyZipEntry(zip, entryName, cover)
                            createdFiles += cover
                            cover.absolutePath
                        } ?: current?.coverUri?.takeIf { File(it).isFile }
                        archivedBook.copy(uri = restoredUri, coverUri = restoredCover)
                    }

                    database.withTransaction {
                        database.shelfFolderDao().insertAll(snapshot.folders)
                        database.bookDao().upsertAll(restoredBooks)
                        database.readerBookmarkDao().insertAll(snapshot.bookmarks)
                        database.readerHighlightDao().insertAll(snapshot.highlights)
                    }
                } catch (error: Exception) {
                    createdFiles.forEach { it.delete() }
                    throw error
                }
                LocalBackupSummary(
                bookCount = snapshot.books.size,
                    fileCount = snapshot.fileEntries.sumOf { entries ->
                        (if (entries.fileEntry != null) 1 else 0) + entries.assetEntries.size +
                            (if (entries.coverEntry != null) 1 else 0)
                    },
                    bookmarkCount = snapshot.bookmarks.size,
                    highlightCount = snapshot.highlights.size
                )
            }
        } finally {
            stagedArchive.delete()
        }
    }

    private fun validateArchive(zip: ZipFile, snapshot: BackupSnapshot) {
        snapshot.fileEntries.forEachIndexed { index, entries ->
            val book = snapshot.books[index]
            entries.fileEntry?.let { name ->
                require(name == "book-files/$index.bin" && zip.getEntry(name) != null) { "备份中的书籍文件缺失" }
            }
            if (entries.fileEntry == null && book.source != "SERVER") {
                error("备份中缺少书籍文件：${book.title}")
            }
            entries.coverEntry?.let { name ->
                require(name == "cover-files/$index.bin" && zip.getEntry(name) != null) { "备份中的封面文件缺失" }
            }
            entries.assetEntries.forEach { name ->
                val prefix = "markdown-assets/$index/"
                val relativePath = name.removePrefix(prefix)
                require(name.startsWith(prefix) && isSafeRelativePath(relativePath) && zip.getEntry(name) != null) {
                    "备份中的 Markdown 资源缺失或路径无效"
                }
            }
        }
    }

    private fun copyZipEntry(zip: ZipFile, entryName: String, destination: File) {
        destination.parentFile?.mkdirs()
        val entry = zip.getEntry(entryName) ?: error("备份文件条目缺失：$entryName")
        try {
            zip.getInputStream(entry).buffered().use { input ->
                destination.outputStream().buffered().use { output -> input.copyTo(output) }
            }
        } catch (error: Exception) {
            destination.delete()
            throw error
        }
    }

    private fun openBookInput(context: Context, rawPath: String) = runCatching {
        val file = File(rawPath)
        if (file.isFile) file.inputStream() else context.contentResolver.openInputStream(Uri.parse(rawPath))
    }.getOrNull()

    private fun collectFiles(directory: File): List<File> = directory.listFiles().orEmpty().flatMap { child ->
        when {
            child.isSymbolicLink() -> emptyList()
            child.isDirectory -> collectFiles(child)
            child.isFile -> listOf(child)
            else -> emptyList()
        }
    }

    private fun File.isSymbolicLink(): Boolean = runCatching {
        canonicalFile != absoluteFile
    }.getOrDefault(true)

    private fun isSafeRelativePath(path: String): Boolean =
        path.isNotBlank() && !path.startsWith('/') && !path.contains('\\') &&
            path.split('/').none { it.isBlank() || it == "." || it == ".." }

    private fun BackupSnapshot.toManifest(addBookFiles: (Int, BookEntity) -> BookArchiveEntries): JSONObject {
        val bookArray = JSONArray()
        val entries = mutableListOf<BookArchiveEntries>()
        books.forEachIndexed { index, book ->
            val bookEntries = addBookFiles(index, book)
            entries += bookEntries
            bookArray.put(book.toJson().apply {
                put("fileEntry", bookEntries.fileEntry)
                put("coverEntry", bookEntries.coverEntry)
                put("assetEntries", JSONArray(bookEntries.assetEntries))
            })
        }
        fileEntries = entries
        return JSONObject()
            .put("app", APP_NAME)
            .put("formatVersion", FORMAT_VERSION)
            .put("createdAt", System.currentTimeMillis())
            .put("books", bookArray)
            .put("folders", JSONArray().apply { folders.forEach { put(it.toJson()) } })
            .put("bookmarks", JSONArray().apply { bookmarks.forEach { put(it.toJson()) } })
            .put("highlights", JSONArray().apply { highlights.forEach { put(it.toJson()) } })
    }

    private fun JSONObject.toSnapshot(): BackupSnapshot {
        val bookArray = getJSONArray("books")
        val books = (0 until bookArray.length()).map { bookArray.getJSONObject(it).toBookEntity() }
        require(books.map(BookEntity::id).distinct().size == books.size) { "备份中包含重复书籍记录" }
        val fileEntries = (0 until bookArray.length()).map { index ->
            val book = bookArray.getJSONObject(index)
            BookArchiveEntries(
                fileEntry = book.optStringOrNull("fileEntry"),
                coverEntry = book.optStringOrNull("coverEntry"),
                assetEntries = book.optJSONArray("assetEntries")?.let { array ->
                    (0 until array.length()).map { array.getString(it) }
                }.orEmpty()
            )
        }
        return BackupSnapshot(
            books = books,
            folders = jsonArray("folders") { it.toFolderEntity() },
            bookmarks = jsonArray("bookmarks") { it.toBookmarkEntity() },
            highlights = jsonArray("highlights") { it.toHighlightEntity() },
            fileEntries = fileEntries
        )
    }

    private inline fun <T> JSONObject.jsonArray(key: String, transform: (JSONObject) -> T): List<T> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).map { transform(array.getJSONObject(it)) }
    }

    private fun BookEntity.toJson() = JSONObject()
        .put("id", id).put("title", title).put("author", author).put("description", description)
        .put("rating", rating).put("tags", tags).put("format", format).put("uri", "")
        .put("sha256", sha256).put("folderId", folderId).put("status", status)
        .put("favorite", favorite).put("importedAt", importedAt).put("lastReadAt", lastReadAt)
        .put("readingDurationSeconds", readingDurationSeconds).put("progressPercent", progressPercent)
        .put("progressChapterHref", progressChapterHref).put("progressChapterTitle", progressChapterTitle)
        .put("progressChapterIndex", progressChapterIndex).put("progressLineIndex", progressLineIndex)
        .put("progressScrollOffset", progressScrollOffset).put("progressPdfZoom", progressPdfZoom)
        .put("progressPositionLabel", progressPositionLabel).put("source", source)
        .put("remoteBookId", remoteBookId).put("shelved", shelved).put("visibleInStore", visibleInStore)

    private fun JSONObject.toBookEntity(): BookEntity {
        val format = getString("format")
        require(runCatching { BookFormat.valueOf(format) }.isSuccess) { "备份包含不支持的书籍格式" }
        return BookEntity(
            id = getString("id"), title = getString("title"), author = optStringOrNull("author"),
            description = optStringOrNull("description"), rating = optDoubleOrNull("rating")?.toFloat(),
            tags = optString("tags"), format = format, uri = optString("uri"),
            sha256 = optStringOrNull("sha256"), folderId = optStringOrNull("folderId"),
            status = optString("status", "UNREAD"), favorite = optBoolean("favorite"),
            importedAt = optLong("importedAt", System.currentTimeMillis()), lastReadAt = optLongOrNull("lastReadAt"),
            readingDurationSeconds = optLong("readingDurationSeconds"), progressPercent = optDouble("progressPercent", 0.0).toFloat(),
            progressChapterHref = optStringOrNull("progressChapterHref"), progressChapterTitle = optStringOrNull("progressChapterTitle"),
            progressChapterIndex = optIntOrNull("progressChapterIndex"), progressLineIndex = optIntOrNull("progressLineIndex"),
            progressScrollOffset = optInt("progressScrollOffset"), progressPdfZoom = optDoubleOrNull("progressPdfZoom")?.toFloat(),
            progressPositionLabel = optStringOrNull("progressPositionLabel"), source = optString("source", "LOCAL"),
            remoteBookId = optLongOrNull("remoteBookId"), shelved = optBoolean("shelved"),
            visibleInStore = optBoolean("visibleInStore", true)
        )
    }

    private fun ShelfFolderEntity.toJson() = JSONObject().put("id", id).put("name", name).put("createdAt", createdAt)
    private fun JSONObject.toFolderEntity() = ShelfFolderEntity(getString("id"), getString("name"), optLong("createdAt"))

    private fun ReaderBookmarkEntity.toJson() = JSONObject()
        .put("id", id).put("bookId", bookId).put("chapterHref", chapterHref).put("chapterTitle", chapterTitle)
        .put("progress", progress).put("chapterIndex", chapterIndex).put("lineIndex", lineIndex)
        .put("scrollOffset", scrollOffset).put("createdAt", createdAt)

    private fun JSONObject.toBookmarkEntity() = ReaderBookmarkEntity(
        id = getString("id"), bookId = getString("bookId"), chapterHref = optStringOrNull("chapterHref"),
        chapterTitle = optStringOrNull("chapterTitle"), progress = optDouble("progress").toFloat(),
        chapterIndex = optIntOrNull("chapterIndex"), lineIndex = optInt("lineIndex"),
        scrollOffset = optInt("scrollOffset"), createdAt = optLong("createdAt")
    )

    private fun ReaderHighlightEntity.toJson() = JSONObject()
        .put("id", id).put("bookId", bookId).put("chapterHref", chapterHref).put("chapterIndex", chapterIndex)
        .put("lineIndex", lineIndex).put("startOffset", startOffset).put("endOffset", endOffset)
        .put("excerpt", excerpt).put("note", note).put("color", color).put("createdAt", createdAt)

    private fun JSONObject.toHighlightEntity() = ReaderHighlightEntity(
        id = getString("id"), bookId = getString("bookId"), chapterHref = optStringOrNull("chapterHref"),
        chapterIndex = optIntOrNull("chapterIndex"), lineIndex = optInt("lineIndex"),
        startOffset = optInt("startOffset"), endOffset = optInt("endOffset"), excerpt = optString("excerpt"),
        note = optStringOrNull("note"), color = optLong("color"), createdAt = optLong("createdAt")
    )

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key) || !has(key)) null else optString(key)

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (isNull(key) || !has(key)) null else optInt(key)

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (isNull(key) || !has(key)) null else optLong(key)

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (isNull(key) || !has(key)) null else optDouble(key)

    private data class BackupSnapshot(
        val books: List<BookEntity>,
        val folders: List<ShelfFolderEntity>,
        val bookmarks: List<ReaderBookmarkEntity>,
        val highlights: List<ReaderHighlightEntity>,
        var fileEntries: List<BookArchiveEntries> = emptyList()
    )

    private data class BookArchiveEntries(
        val fileEntry: String?,
        val coverEntry: String?,
        val assetEntries: List<String>
    )
}
