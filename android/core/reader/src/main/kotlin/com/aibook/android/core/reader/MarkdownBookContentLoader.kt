package com.aibook.android.core.reader

import com.aibook.android.core.model.BookFormat
import org.commonmark.node.Block
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Image
import org.commonmark.node.Code
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TableCell
import org.jsoup.Jsoup
import java.io.File
import java.net.URI
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MarkdownBookContentLoader(
    private val parser: Parser = Parser.builder()
        .extensions(listOf(TablesExtension.create()))
        .build(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val readMarkdown: (File) -> String = { it.readText() }
) : BookContentLoader {

    override val supportedFormats: Set<BookFormat> = setOf(BookFormat.MARKDOWN)

    override suspend fun load(request: BookContentRequest): BookContentResult {
        if (request.format !in supportedFormats) {
            return BookContentResult.Failure(BookContentError.UnsupportedVariant)
        }
        if (!request.file.exists()) {
            return BookContentResult.Failure(BookContentError.FileMissing)
        }

        return runCatching {
            withContext(ioDispatcher) {
                parse(readMarkdown(request.file), requireNotNull(request.file.parentFile).canonicalFile)
            }
        }.fold(
            onSuccess = { BookContentResult.Success(it) },
            onFailure = { BookContentResult.Failure(BookContentError.CorruptedFile) }
        )
    }

    private fun parse(markdown: String, resourceRoot: File): ReaderBookContent {
        val document = parser.parse(markdown)
        val chapters = mutableListOf<ReaderChapter>()
        val pendingBlocks = mutableListOf<String>()
        var chapterTitle: String? = null
        var chapterImagePath: String? = null

        fun flushChapter() {
            val content = pendingBlocks
                .map(String::trim)
                .filter(String::isNotBlank)
                .joinToString("\n")
            if (content.isBlank() && chapterTitle == null && chapterImagePath == null) return
            val index = chapters.size
            chapters += ReaderChapter(
                index = index,
                title = chapterTitle ?: if (chapters.isEmpty()) "前言" else "正文",
                href = "markdown:$index",
                content = content,
                imageUri = chapterImagePath
            )
            pendingBlocks.clear()
            chapterImagePath = null
        }

        var node = document.firstChild
        while (node != null) {
            if (node is Heading && node.level <= 2) {
                flushChapter()
                chapterTitle = renderText(node).ifBlank { "第 ${chapters.size + 1} 章" }
            } else {
                renderBlock(node).takeIf(String::isNotBlank)?.let(pendingBlocks::add)
            }
            if (chapterImagePath == null) {
                chapterImagePath = findFirstSafeImage(node, resourceRoot)
            }
            node = node.next
        }
        flushChapter()

        if (chapters.isEmpty()) {
            chapters += ReaderChapter(0, "正文", "markdown:0", renderText(document))
        } else if (chapters.size == 1 && chapterTitle == null) {
            chapters[0] = chapters[0].copy(title = "正文")
        }

        return ReaderBookContent(
            title = chapters.firstOrNull { it.title != "前言" && it.title != "正文" }?.title,
            chapters = chapters
        )
    }

    private fun findFirstSafeImage(node: Node, resourceRoot: File): String? {
        if (node is Image) {
            val destination = MarkdownResourceReferences.safeRelativeRasterPath(node.destination)
            if (destination != null) {
                val candidate = runCatching { File(resourceRoot, destination).canonicalFile }.getOrNull()
                if (candidate != null && candidate.isFile && candidate.toPath().startsWith(resourceRoot.toPath())) {
                    return candidate.path
                }
            }
        }
        var child = node.firstChild
        while (child != null) {
            findFirstSafeImage(child, resourceRoot)?.let { return it }
            child = child.next
        }
        return null
    }

    private fun renderBlock(node: Node): String = when (node) {
        is BulletList -> renderList(node, ordered = false)
        is OrderedList -> renderList(node, ordered = true, startNumber = node.markerStartNumber)
        is BlockQuote -> renderText(node)
            .lineSequence()
            .filter(String::isNotBlank)
            .joinToString("\n") { "> $it" }
        is FencedCodeBlock -> node.literal.trimEnd()
        is IndentedCodeBlock -> node.literal.trimEnd()
        is ThematicBreak -> "———"
        is HtmlBlock -> Jsoup.parseBodyFragment(node.literal).text()
        is TableBlock -> renderTable(node)
        else -> renderText(node)
    }

    private fun renderTable(table: TableBlock): String {
        val rows = mutableListOf<String>()
        fun visit(node: Node) {
            if (node is TableRow) {
                val cells = mutableListOf<String>()
                var cell = node.firstChild
                while (cell != null) {
                    if (cell is TableCell) cells += renderText(cell)
                    cell = cell.next
                }
                rows += cells.joinToString(" | ")
            } else {
                var child = node.firstChild
                while (child != null) {
                    visit(child)
                    child = child.next
                }
            }
        }
        visit(table)
        return rows.joinToString("\n")
    }

    private fun renderList(node: Block, ordered: Boolean, startNumber: Int = 1): String {
        val lines = mutableListOf<String>()
        var child = node.firstChild
        var index = startNumber
        while (child != null) {
            if (child is ListItem) {
                val text = renderText(child).lineSequence().filter(String::isNotBlank).joinToString(" ")
                lines += if (ordered) "${index++}. $text" else "• $text"
            }
            child = child.next
        }
        return lines.joinToString("\n")
    }

    private fun renderText(node: Node): String = buildString {
        appendNodeText(node)
    }.trim()

    private fun StringBuilder.appendNodeText(node: Node) {
        when (node) {
            is Text -> append(node.literal)
            is Code -> append(node.literal)
            is SoftLineBreak, is HardLineBreak -> append('\n')
            is HtmlInline -> append(Jsoup.parseBodyFragment(node.literal).text())
            is Link -> {
                appendChildrenText(node)
                if (node.destination.isNotBlank()) append(" (${node.destination})")
            }
            else -> appendChildrenText(node)
        }
    }

    private fun StringBuilder.appendChildrenText(node: Node) {
        var child = node.firstChild
        while (child != null) {
            appendNodeText(child)
            child = child.next
        }
    }
}

/** Extracts only local raster-image paths that can safely be copied beside a Markdown book. */
object MarkdownResourceReferences {
    private val parser = Parser.builder().build()
    private val rasterExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")

    fun extract(markdown: String): List<String> {
        val references = linkedSetOf<String>()
        fun visit(node: Node) {
            if (node is Image) safeRelativeRasterPath(node.destination)?.let(references::add)
            var child = node.firstChild
            while (child != null) {
                visit(child)
                child = child.next
            }
        }
        visit(parser.parse(markdown))
        return references.toList()
    }

    internal fun safeRelativeRasterPath(destination: String): String? {
        val trimmed = destination.trim()
        if (trimmed.isEmpty() || trimmed.contains('\\')) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        if (uri.isAbsolute || uri.rawAuthority != null || uri.rawQuery != null || uri.rawFragment != null) return null
        val decoded = uri.path?.takeIf(String::isNotBlank) ?: return null
        val file = File(decoded)
        if (file.isAbsolute) return null
        val segments = decoded.split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." }) return null
        if (file.extension.lowercase() !in rasterExtensions) return null
        return segments.joinToString("/")
    }
}
