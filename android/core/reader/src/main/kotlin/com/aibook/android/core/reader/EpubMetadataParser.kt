package com.aibook.android.core.reader

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

data class EpubMetadata(
    val title: String? = null,
    val author: String? = null,
    val language: String? = null,
    val coverImage: EpubImage? = null
)

data class EpubImage(
    val href: String,
    val mediaType: String,
    val bytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EpubImage) return false
        return href == other.href &&
            mediaType == other.mediaType &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = href.hashCode()
        result = 31 * result + mediaType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

object EpubMetadataParser {
    fun parse(bytes: ByteArray): EpubMetadata {
        val entries = unzip(bytes)
        val opfPath = entries.keys.firstOrNull { it.endsWith(".opf", ignoreCase = true) }
            ?: return EpubMetadata()
        val opf = entries[opfPath] ?: return EpubMetadata()
        val document = DocumentBuilderFactory.newInstance()
            .apply { configureSafely() }
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(opf))
        val root = document.documentElement
        val manifest = root.manifestItems()
        val coverItem = root.coverItem(manifest)
        val basePath = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
        val coverImage = coverItem?.let { item ->
            val imagePath = resolvePath(basePath, item.href)
            entries[imagePath]?.let { imageBytes ->
                EpubImage(href = item.href, mediaType = item.mediaType, bytes = imageBytes)
            }
        }

        return EpubMetadata(
            title = root.firstTextByLocalName("title"),
            author = root.firstTextByLocalName("creator"),
            language = root.firstTextByLocalName("language"),
            coverImage = coverImage
        )
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

    private fun Element.firstTextByLocalName(name: String): String? {
        val nodes = getElementsByTagNameNS("*", name)
        if (nodes.length == 0) {
            return null
        }

        return nodes.item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun Element.manifestItems(): Map<String, ManifestItem> {
        val items = linkedMapOf<String, ManifestItem>()
        val nodes = getElementsByTagNameNS("*", "item")
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as? Element ?: continue
            val id = element.getAttribute("id").takeIf { it.isNotBlank() } ?: continue
            val href = element.getAttribute("href").takeIf { it.isNotBlank() } ?: continue
            items[id] = ManifestItem(
                id = id,
                href = href,
                mediaType = element.getAttribute("media-type"),
                properties = element.getAttribute("properties")
            )
        }
        return items
    }

    private fun Element.coverItem(manifest: Map<String, ManifestItem>): ManifestItem? {
        val metaNodes = getElementsByTagNameNS("*", "meta")
        for (i in 0 until metaNodes.length) {
            val element = metaNodes.item(i) as? Element ?: continue
            if (element.getAttribute("name").equals("cover", ignoreCase = true)) {
                manifest[element.getAttribute("content")]?.let { return it }
            }
        }
        return manifest.values.firstOrNull { item ->
            item.properties.splitWhitespace().any { it.equals("cover-image", ignoreCase = true) }
        }
    }

    private fun resolvePath(basePath: String, href: String): String {
        val cleanHref = href.substringBefore('#')
        return if (basePath.isBlank()) {
            URI(null, null, cleanHref, null).normalize().path
        } else {
            URI(null, null, "$basePath/$cleanHref", null).normalize().path
        }
    }

    private fun String.splitWhitespace(): List<String> =
        trim().split(Regex("\\s+")).filter { it.isNotBlank() }

    private fun DocumentBuilderFactory.configureSafely() {
        isNamespaceAware = true
        setFeatureIfSupported("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeatureIfSupported("http://xml.org/sax/features/external-general-entities", false)
        setFeatureIfSupported("http://xml.org/sax/features/external-parameter-entities", false)
    }

    private fun DocumentBuilderFactory.setFeatureIfSupported(feature: String, enabled: Boolean) {
        runCatching { setFeature(feature, enabled) }
    }

    private data class ManifestItem(
        val id: String,
        val href: String,
        val mediaType: String,
        val properties: String
    )
}
