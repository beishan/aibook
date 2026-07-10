package com.aibook.android.core.reader

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.Base64
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

data class EpubBookContent(
    val metadata: EpubMetadata = EpubMetadata(),
    val chapters: List<ReaderChapter> = emptyList()
) {
    val fullText: String = chapters.joinToString("\n\n") { chapter ->
        buildString {
            appendLine(chapter.title)
            append(chapter.content)
        }.trim()
    }
}

object EpubContentParser {
    fun parse(bytes: ByteArray): EpubBookContent {
        val entries = unzip(bytes)
        val opfPath = findOpfPath(entries) ?: entries.keys.firstOrNull { it.endsWith(".opf", ignoreCase = true) }
            ?: return EpubBookContent()
        val opfBytes = entries[opfPath] ?: return EpubBookContent()
        val opf = parseXml(opfBytes)
        val metadata = parseMetadata(opf)
        val manifest = parseManifest(opf)
        val spineIds = parseSpineIds(opf)
        val basePath = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
        val documentItems = if (spineIds.isNotEmpty()) {
            spineIds.mapNotNull { manifest[it] }
        } else {
            manifest.values.filter { it.mediaType.contains("html", ignoreCase = true) }
        }

        val chapters = documentItems.mapIndexedNotNull { index, item ->
            val path = resolvePath(basePath, item.href)
            val documentBytes = entries[path] ?: return@mapIndexedNotNull null
            parseChapter(index, path, documentBytes, entries)
        }

        return EpubBookContent(metadata = metadata, chapters = chapters)
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return entries
    }

    private fun findOpfPath(entries: Map<String, ByteArray>): String? {
        val container = entries["META-INF/container.xml"] ?: return null
        val document = parseXml(container)
        val rootfiles = document.getElementsByTagNameNS("*", "rootfile")
        for (i in 0 until rootfiles.length) {
            val element = rootfiles.item(i) as? Element ?: continue
            val path = element.getAttribute("full-path")
            if (path.isNotBlank()) return path
        }
        return null
    }

    private fun parseMetadata(opf: Document): EpubMetadata {
        val root = opf.documentElement
        return EpubMetadata(
            title = root.firstTextByLocalName("title"),
            author = root.firstTextByLocalName("creator"),
            language = root.firstTextByLocalName("language"),
            description = root.firstTextByLocalName("description")
        )
    }

    private fun parseManifest(opf: Document): Map<String, ManifestItem> {
        val items = linkedMapOf<String, ManifestItem>()
        val nodes = opf.getElementsByTagNameNS("*", "item")
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as? Element ?: continue
            val id = element.getAttribute("id").takeIf { it.isNotBlank() } ?: continue
            val href = element.getAttribute("href").takeIf { it.isNotBlank() } ?: continue
            val mediaType = element.getAttribute("media-type")
            items[id] = ManifestItem(
                id = id,
                href = href,
                mediaType = mediaType,
                properties = element.getAttribute("properties")
            )
        }
        return items
    }

    private fun parseSpineIds(opf: Document): List<String> {
        val ids = mutableListOf<String>()
        val nodes = opf.getElementsByTagNameNS("*", "itemref")
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as? Element ?: continue
            val idref = element.getAttribute("idref")
            if (idref.isNotBlank()) ids += idref
        }
        return ids
    }

    private fun parseChapter(
        index: Int,
        href: String,
        bytes: ByteArray,
        entries: Map<String, ByteArray>
    ): ReaderChapter {
        val document = parseXml(sanitizeXhtml(bytes).toByteArray())
        val body = document.getElementsByTagNameNS("*", "body").item(0) ?: document.documentElement
        val image = firstImage(body, href, entries)
        val title = firstHeading(body)
            ?: image?.alt?.takeIf { it.isNotBlank() }
            ?: if (image != null) "封面" else "第${index + 1}章"
        val paragraphs = mutableListOf<String>()
        collectBlockText(body, paragraphs)
        val content = paragraphs
            .map { it.trimWhitespace() }
            .filter { it.isNotBlank() && it != title }
            .joinToString("\n\n")

        return ReaderChapter(
            index = index,
            title = title.trimWhitespace(),
            href = href,
            content = content,
            imageUri = image?.dataUri
        )
    }

    private fun firstImage(node: Node, chapterHref: String, entries: Map<String, ByteArray>): ChapterImage? {
        if (node is Element && node.localName?.lowercase() == "img") {
            val src = node.getAttribute("src").takeIf { it.isNotBlank() }
                ?: node.getAttribute("href").takeIf { it.isNotBlank() }
            if (src != null) {
                val chapterBasePath = chapterHref.substringBeforeLast('/', missingDelimiterValue = "")
                val imagePath = resolvePath(chapterBasePath, src)
                val bytes = entries[imagePath]
                if (bytes != null) {
                    val mediaType = mediaTypeFor(imagePath)
                    val encoded = Base64.getEncoder().encodeToString(bytes)
                    return ChapterImage(
                        dataUri = "data:$mediaType;base64,$encoded",
                        alt = node.getAttribute("alt")
                    )
                }
            }
        }
        val children = node.childNodes
        for (i in 0 until children.length) {
            firstImage(children.item(i), chapterHref, entries)?.let { return it }
        }
        return null
    }

    private fun firstHeading(node: Node): String? {
        if (node is Element && node.localName?.lowercase() in headingNames) {
            return node.textContent?.trimWhitespace()?.takeIf { it.isNotBlank() }
        }
        val children = node.childNodes
        for (i in 0 until children.length) {
            firstHeading(children.item(i))?.let { return it }
        }
        return null
    }

    private fun collectBlockText(node: Node, output: MutableList<String>) {
        if (node is Element && node.localName?.lowercase() in blockNames) {
            node.textContent?.trimWhitespace()?.takeIf { it.isNotBlank() }?.let(output::add)
            return
        }
        val children = node.childNodes
        for (i in 0 until children.length) {
            collectBlockText(children.item(i), output)
        }
    }

    private fun parseXml(bytes: ByteArray): Document {
        return DocumentBuilderFactory.newInstance()
            .apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(bytes))
    }

    private fun sanitizeXhtml(bytes: ByteArray): String {
        return bytes.toString(Charsets.UTF_8)
            .replace(Regex("<!DOCTYPE[\\s\\S]*?>", RegexOption.IGNORE_CASE), "")
            .replace("&nbsp;", " ")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&hellip;", "…")
            .replace("&ldquo;", "“")
            .replace("&rdquo;", "”")
            .replace("&lsquo;", "‘")
            .replace("&rsquo;", "’")
            .replace("&copy;", "©")
    }

    private fun resolvePath(basePath: String, href: String): String {
        val cleanHref = href.substringBefore('#')
        return if (basePath.isBlank()) {
            URI(null, null, cleanHref, null).normalize().path
        } else {
            URI(null, null, "$basePath/$cleanHref", null).normalize().path
        }
    }

    private fun Element.firstTextByLocalName(name: String): String? {
        val nodes = getElementsByTagNameNS("*", name)
        if (nodes.length == 0) return null
        return nodes.item(0)?.textContent?.trimWhitespace()?.takeIf { it.isNotBlank() }
    }

    private fun String.trimWhitespace(): String =
        replace(Regex("[\\t\\n\\r ]+"), " ").trim()

    private data class ManifestItem(
        val id: String,
        val href: String,
        val mediaType: String,
        val properties: String
    )

    private data class ChapterImage(
        val dataUri: String,
        val alt: String?
    )

    private fun mediaTypeFor(path: String): String {
        return when (path.substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            else -> "application/octet-stream"
        }
    }

    private val headingNames = setOf("h1", "h2", "h3", "h4", "h5", "h6", "title")
    private val blockNames = setOf("p", "div", "section", "blockquote", "li")
}
