package com.aibook.android.core.reader

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

data class EpubMetadata(
    val title: String? = null,
    val author: String? = null,
    val language: String? = null
)

object EpubMetadataParser {
    fun parse(bytes: ByteArray): EpubMetadata {
        val opf = findOpf(bytes) ?: return EpubMetadata()
        val document = DocumentBuilderFactory.newInstance()
            .apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(opf))
        val root = document.documentElement

        return EpubMetadata(
            title = root.firstTextByLocalName("title"),
            author = root.firstTextByLocalName("creator"),
            language = root.firstTextByLocalName("language")
        )
    }

    private fun findOpf(bytes: ByteArray): ByteArray? {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".opf", ignoreCase = true)) {
                    return zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        return null
    }

    private fun Element.firstTextByLocalName(name: String): String? {
        val nodes = getElementsByTagNameNS("*", name)
        if (nodes.length == 0) {
            return null
        }

        return nodes.item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }
    }
}
