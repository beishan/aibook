package com.aibook.android.core.mobi

import com.aibook.android.core.model.BookFormat
import com.aibook.android.core.reader.BookContentError
import com.aibook.android.core.reader.BookContentLoader
import com.aibook.android.core.reader.BookContentRequest
import com.aibook.android.core.reader.BookContentResult
import com.aibook.android.core.reader.ReaderBookContent
import com.aibook.android.core.reader.ReaderChapter
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

class MobiBookContentLoader(
    private val parser: MobiDocumentParser,
    private val parserVersion: String = "libmobi-0.12-reader-v3"
) : BookContentLoader {

    override val supportedFormats: Set<BookFormat> = setOf(BookFormat.MOBI, BookFormat.AZW3)

    override suspend fun load(request: BookContentRequest): BookContentResult = withContext(Dispatchers.IO) {
        if (request.format !in supportedFormats) {
            return@withContext BookContentResult.Failure(BookContentError.UnsupportedVariant)
        }
        if (!request.file.isFile) {
            return@withContext BookContentResult.Failure(BookContentError.FileMissing)
        }

        val sourceHash = sha256(request.file)
        val contentHash = request.contentHash
            ?.lowercase()
            ?.takeIf { it.matches(Regex("[a-f0-9]{64}")) }
            ?.takeIf { it == sourceHash }
            ?: sourceHash
        val cacheDirectory = File(request.cacheDirectory, contentHash)
        val fingerprint = "$parserVersion|${request.format.extension}|$contentHash"
        loadCache(cacheDirectory, fingerprint)?.let {
            return@withContext BookContentResult.Success(it)
        }

        prepareEmptyDirectory(cacheDirectory)
            ?: return@withContext BookContentResult.Failure(BookContentError.InsufficientStorage)
        val rawDirectory = File(cacheDirectory, "raw").apply { mkdirs() }
        when (val parsed = parser.parse(request.file.path, rawDirectory.path)) {
            is MobiParseResult.Failure -> BookContentResult.Failure(parsed.error.toContentError())
            is MobiParseResult.Success -> runCatching {
                normalize(parsed.document).also { content ->
                    writeCache(cacheDirectory, fingerprint, content)
                }
            }.fold(
                onSuccess = { BookContentResult.Success(it) },
                onFailure = { BookContentResult.Failure(BookContentError.CorruptedFile) }
            )
        }
    }

    private fun normalize(document: MobiDocument): ReaderBookContent {
        val chapters = document.chapters.flatMapIndexed(::normalizeChapter)
            .filter { it.content.isNotBlank() || !it.imageUri.isNullOrBlank() }
        require(chapters.isNotEmpty())
        return ReaderBookContent(
            title = document.title,
            author = document.author,
            coverPath = document.coverPath?.let { File(it).canonicalPath },
            chapters = chapters.mapIndexed { index, chapter -> chapter.copy(index = index) }
        )
    }

    private fun normalizeChapter(rawIndex: Int, chapter: MobiChapter): List<ReaderChapter> {
        val htmlFile = File(chapter.htmlPath)
        require(htmlFile.isFile)
        val html = Jsoup.parse(htmlFile, Charsets.UTF_8.name())
        val baseHref = chapter.href.ifBlank { "mobi:$rawIndex" }
        val fallbackTitle = chapter.title?.takeIf(String::isNotBlank)
            ?: html.title().takeIf(String::isNotBlank)
            ?: "第 ${rawIndex + 1} 章"
        val imagePath = html.selectFirst("img[src]")
            ?.attr("src")
            ?.takeIf(String::isNotBlank)
            ?.let { source ->
                runCatching {
                    val resourceRoot = requireNotNull(htmlFile.parentFile).canonicalFile
                    File(resourceRoot, source).canonicalFile.takeIf { candidate ->
                        candidate.isFile && candidate.path.startsWith(resourceRoot.path + File.separator)
                    }
                }.getOrNull()
            }
            ?.path
        val blocks = html.body().select("h1, h2, h3, h4, h5, h6, p, li, pre")
        if (blocks.isEmpty()) {
            return listOf(ReaderChapter(0, fallbackTitle, baseHref, html.body().text().trim(), imagePath))
        }

        val result = mutableListOf<ReaderChapter>()
        val content = mutableListOf<String>()
        var title = fallbackTitle

        fun flush() {
            val text = content.filter(String::isNotBlank).joinToString("\n")
            if (text.isBlank()) return
            val section = result.size
            result += ReaderChapter(
                index = 0,
                title = title,
                href = if (section == 0) baseHref else "$baseHref#section-$section",
                content = text,
                imageUri = imagePath.takeIf { section == 0 }
            )
            content.clear()
        }

        blocks.forEach { block ->
            val text = if (block.tagName() == "li") {
                renderListItemText(block)
            } else {
                block.text().trim()
            }
            if (block.tagName() == "h1" || block.tagName() == "h2") {
                flush()
                title = text.ifBlank { fallbackTitle }
            } else if (text.isNotBlank()) {
                content += text
            }
        }
        flush()
        return result.ifEmpty {
            listOf(ReaderChapter(0, fallbackTitle, baseHref, html.body().text().trim(), imagePath))
        }
    }

    private fun closestListItem(element: org.jsoup.nodes.Element): org.jsoup.nodes.Element? {
        var current: org.jsoup.nodes.Element? = element
        while (current != null) {
            if (current.tagName() == "li") return current
            current = current.parent()
        }
        return null
    }

    private fun renderListItemText(item: org.jsoup.nodes.Element): String = item.allElements
        .filter { element -> closestListItem(element) === item }
        .filterNot { element -> isInsideParagraph(element, item) }
        .flatMap { element -> element.textNodes() }
        .mapNotNull { textNode -> textNode.text().trim().takeIf(String::isNotBlank) }
        .joinToString(" ")

    private fun isInsideParagraph(
        element: org.jsoup.nodes.Element,
        listItem: org.jsoup.nodes.Element
    ): Boolean {
        var current: org.jsoup.nodes.Element? = element
        while (current != null && current !== listItem) {
            if (current.tagName() == "p") return true
            current = current.parent()
        }
        return false
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun loadCache(directory: File, fingerprint: String): ReaderBookContent? = runCatching {
        if (File(directory, CACHE_INFO).takeIf(File::isFile)?.readText() != fingerprint) return null
        val metadata = File(directory, METADATA).readLines().associate { line ->
            line.substringBefore('=') to decode(line.substringAfter('=', ""))
        }
        val canonicalDirectory = directory.canonicalFile
        val chapters = File(directory, MANIFEST).readLines().map { line ->
            val columns = line.split('\t')
            require(columns.size == 7)
            val contentFile = File(directory, columns[3]).canonicalFile
            require(contentFile.path.startsWith(canonicalDirectory.path + File.separator))
            require(contentFile.isFile)
            require(sha256(contentFile) == columns[5])
            val imagePath = validatedResourcePath(
                path = decode(columns[4]),
                expectedHash = columns[6],
                canonicalDirectory = canonicalDirectory
            )
            ReaderChapter(
                index = columns[0].toInt(),
                href = decode(columns[1]),
                title = decode(columns[2]),
                content = contentFile.readText(),
                imageUri = imagePath
            )
        }
        require(chapters.isNotEmpty())
        ReaderBookContent(
            title = metadata["title"]?.ifBlank { null },
            author = metadata["author"]?.ifBlank { null },
            coverPath = validatedResourcePath(
                path = metadata["cover"].orEmpty(),
                expectedHash = metadata["coverSha256"].orEmpty(),
                canonicalDirectory = canonicalDirectory
            ),
            chapters = chapters
        )
    }.getOrNull()

    private fun validatedResourcePath(
        path: String,
        expectedHash: String,
        canonicalDirectory: File
    ): String? {
        if (path.isBlank()) {
            require(expectedHash.isBlank())
            return null
        }
        require(expectedHash.matches(Regex("[a-f0-9]{64}")))
        val resource = File(path).canonicalFile
        require(resource.path.startsWith(canonicalDirectory.path + File.separator))
        require(resource.isFile && resource.length() > 0L)
        require(sha256(resource) == expectedHash)
        return resource.path
    }

    private fun writeCache(directory: File, fingerprint: String, content: ReaderBookContent) {
        val normalized = File(directory, "normalized").apply { mkdirs() }
        val canonicalDirectory = directory.canonicalFile
        val coverHash = resourceHash(content.coverPath, canonicalDirectory)
        File(directory, METADATA).writeText(
            listOf(
                "title=${encode(content.title.orEmpty())}",
                "author=${encode(content.author.orEmpty())}",
                "cover=${encode(content.coverPath.orEmpty())}",
                "coverSha256=$coverHash"
            ).joinToString("\n")
        )
        File(directory, MANIFEST).bufferedWriter().use { manifest ->
            content.chapters.forEach { chapter ->
                val name = "chapter-${chapter.index.toString().padStart(5, '0')}.txt"
                val contentFile = File(normalized, name).apply { writeText(chapter.content) }
                val imageHash = resourceHash(chapter.imageUri, canonicalDirectory)
                manifest.appendLine(
                    "${chapter.index}\t${encode(chapter.href)}\t${encode(chapter.title)}\t" +
                        "normalized/$name\t${encode(chapter.imageUri.orEmpty())}\t${sha256(contentFile)}\t" +
                        imageHash
                )
            }
        }
        File(directory, CACHE_INFO).writeText(fingerprint)
    }

    private fun resourceHash(path: String?, canonicalDirectory: File): String {
        if (path.isNullOrBlank()) return ""
        val resource = File(path).canonicalFile
        require(resource.path.startsWith(canonicalDirectory.path + File.separator))
        require(resource.isFile && resource.length() > 0L)
        return sha256(resource)
    }

    private fun prepareEmptyDirectory(directory: File): File? = runCatching {
        if (directory.exists()) directory.deleteRecursively()
        require(directory.mkdirs() || directory.isDirectory)
        directory
    }.getOrNull()

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8.name())

    private fun MobiParseError.toContentError(): BookContentError = when (this) {
        MobiParseError.FILE_MISSING -> BookContentError.FileMissing
        MobiParseError.DRM_PROTECTED -> BookContentError.DrmProtected
        MobiParseError.UNSUPPORTED_VARIANT -> BookContentError.UnsupportedVariant
        MobiParseError.CORRUPTED_FILE -> BookContentError.CorruptedFile
        MobiParseError.INSUFFICIENT_STORAGE -> BookContentError.InsufficientStorage
        MobiParseError.PARSE_FAILED -> BookContentError.ParseFailed("MOBI/AZW3 解析失败")
    }

    private companion object {
        const val CACHE_INFO = "cache.info"
        const val METADATA = "metadata.properties"
        const val MANIFEST = "reader.manifest"
    }
}
