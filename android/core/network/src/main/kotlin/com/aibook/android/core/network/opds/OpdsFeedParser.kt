package com.aibook.android.core.network.opds

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

class OpdsFeedParser {
    fun parse(xml: String): OpdsFeed {
        if (xml.isBlank()) {
            return OpdsFeed(title = "", entries = emptyList())
        }

        val document = DocumentBuilderFactory.newInstance()
            .apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

        val feed = document.documentElement
        val title = feed.directChildText("title").orEmpty()
        val entries = feed.directChildren("entry").map { entry ->
            val links = entry.links()
            OpdsEntry(
                title = entry.directChildText("title").orEmpty(),
                author = entry.directChildren("author").firstOrNull()?.directChildText("name"),
                summary = entry.directChildText("summary") ?: entry.directChildText("content"),
                acquisitionLink = links.firstOrNull {
                    it.rel == "http://opds-spec.org/acquisition" ||
                        it.rel?.startsWith("http://opds-spec.org/acquisition/") == true
                },
                alternateLink = links.firstOrNull {
                    it.rel == "alternate" ||
                        it.rel == "subsection" ||
                        it.rel == "http://opds-spec.org/sort/new" ||
                        it.rel == "http://opds-spec.org/sort/popular"
                },
                coverLink = links.firstOrNull {
                    it.rel == "http://opds-spec.org/image" ||
                        it.rel == "http://opds-spec.org/image/thumbnail"
                }
            )
        }

        return OpdsFeed(title = title, entries = entries)
    }

    private fun Element.directChildren(name: String): List<Element> {
        val children = childNodes
        val result = mutableListOf<Element>()

        for (index in 0 until children.length) {
            val node = children.item(index)
            if (node is Element && node.localName == name) {
                result += node
            }
        }

        return result
    }

    private fun Element.directChildText(name: String): String? {
        return directChildren(name).firstOrNull()?.textContent?.trim()
    }

    private fun Element.links(): List<OpdsLink> {
        return directChildren("link").mapNotNull { link ->
            val href = link.getAttribute("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            OpdsLink(
                href = href,
                type = link.getAttribute("type").takeIf { it.isNotBlank() },
                rel = link.getAttribute("rel").takeIf { it.isNotBlank() }
            )
        }
    }
}
