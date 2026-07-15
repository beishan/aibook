package com.aibook.android.core.network.opds

import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import org.w3c.dom.Element

class OpdsFeedParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(payload: String): OpdsFeed {
        val trimmed = payload.trim()
        if (trimmed.isEmpty()) return OpdsFeed(title = "", entries = emptyList())
        return if (trimmed.startsWith('{')) parseJson(trimmed) else parseAtom(trimmed)
    }

    private fun parseAtom(xml: String): OpdsFeed {
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
        val entries = feed.directChildren("entry").map { entry ->
            val links = entry.links()
            OpdsEntry(
                title = entry.directChildText("title").orEmpty(),
                identifier = entry.directChildText("id"),
                author = entry.directChildren("author").firstOrNull()?.directChildText("name"),
                summary = entry.directChildText("summary") ?: entry.directChildText("content"),
                acquisitionLink = links.firstOrNull { it.isAcquisition() },
                alternateLink = links.firstOrNull { it.isNavigation() },
                coverLink = links.firstOrNull { it.isCover() },
                categories = entry.directChildren("category").mapNotNull {
                    it.getAttribute("term").takeIf(String::isNotBlank) ?: it.textContent?.trim()?.takeIf(String::isNotBlank)
                },
                modifiedAt = entry.directChildText("updated")
            )
        }
        val feedLinks = feed.links()
        return OpdsFeed(
            title = feed.directChildText("title").orEmpty(),
            entries = entries,
            nextLink = feedLinks.firstOrNull { it.relTokens().contains("next") }
        )
    }

    private fun parseJson(payload: String): OpdsFeed {
        val root = json.parseToJsonElement(payload).jsonObject
        val metadata = root["metadata"] as? JsonObject
        val entries = mutableListOf<OpdsEntry>()
        entries += publications(root["publications"] as? JsonArray, inheritedCategory = null)
        entries += navigation(root["navigation"] as? JsonArray)
        entries += navigation(root["catalogs"] as? JsonArray)
        (root["groups"] as? JsonArray)?.forEach { groupElement ->
            val group = groupElement as? JsonObject ?: return@forEach
            val groupTitle = (group["metadata"] as? JsonObject)?.stringValue("title")
            entries += publications(group["publications"] as? JsonArray, groupTitle)
            entries += navigation(group["navigation"] as? JsonArray)
        }
        val links = links(root["links"] as? JsonArray)
        return OpdsFeed(
            title = metadata?.stringValue("title").orEmpty(),
            entries = entries,
            nextLink = links.firstOrNull { it.relTokens().contains("next") }
        )
    }

    private fun publications(items: JsonArray?, inheritedCategory: String?): List<OpdsEntry> = items.orEmpty().mapNotNull { element ->
        val publication = element as? JsonObject ?: return@mapNotNull null
        val metadata = publication["metadata"] as? JsonObject ?: JsonObject(emptyMap())
        val links = links(publication["links"] as? JsonArray)
        val title = metadata.stringValue("title")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        OpdsEntry(
            title = title,
            identifier = metadata.stringValue("identifier"),
            author = contributorName(metadata["author"]),
            summary = metadata.stringValue("description"),
            acquisitionLink = links.firstOrNull { it.isAcquisition() },
            alternateLink = links.firstOrNull { it.relTokens().any { rel -> rel == "alternate" || rel == "self" } },
            coverLink = links.firstOrNull { it.isCover() },
            categories = (subjects(metadata["subject"]) + listOfNotNull(inheritedCategory)).distinct(),
            modifiedAt = metadata.stringValue("modified")
        )
    }

    private fun navigation(items: JsonArray?): List<OpdsEntry> = items.orEmpty().mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        val title = item.stringValue("title")
            ?: (item["metadata"] as? JsonObject)?.stringValue("title")
            ?: return@mapNotNull null
        val link = links(item["links"] as? JsonArray).firstOrNull()
            ?: item.stringValue("href")?.let { OpdsLink(it, item.stringValue("type"), item.stringValue("rel")) }
            ?: return@mapNotNull null
        OpdsEntry(title = title, alternateLink = link)
    }

    private fun links(items: JsonArray?): List<OpdsLink> = items.orEmpty().mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        val href = item.stringValue("href")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        val rel = when (val value = item["rel"]) {
            is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString(" ")
            is JsonPrimitive -> value.contentOrNull
            else -> null
        }
        OpdsLink(href = href, type = item.stringValue("type"), rel = rel)
    }

    private fun contributorName(value: JsonElement?): String? = when (value) {
        is JsonPrimitive -> value.contentOrNull
        is JsonObject -> value.stringValue("name")
        is JsonArray -> value.firstNotNullOfOrNull { contributorName(it) }
        else -> null
    }

    private fun subjects(value: JsonElement?): List<String> = when (value) {
        is JsonPrimitive -> listOfNotNull(value.contentOrNull)
        is JsonObject -> listOfNotNull(value.stringValue("name") ?: value.stringValue("code"))
        is JsonArray -> value.flatMap(::subjects)
        else -> emptyList()
    }

    private fun JsonObject.stringValue(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun Element.directChildren(name: String): List<Element> {
        val result = mutableListOf<Element>()
        for (index in 0 until childNodes.length) {
            val node = childNodes.item(index)
            if (node is Element && node.localName == name) result += node
        }
        return result
    }

    private fun Element.directChildText(name: String): String? = directChildren(name).firstOrNull()?.textContent?.trim()

    private fun Element.links(): List<OpdsLink> = directChildren("link").mapNotNull { link ->
        val href = link.getAttribute("href").takeIf(String::isNotBlank) ?: return@mapNotNull null
        OpdsLink(
            href = href,
            type = link.getAttribute("type").takeIf(String::isNotBlank),
            rel = link.getAttribute("rel").takeIf(String::isNotBlank)
        )
    }

    private fun OpdsLink.relTokens(): Set<String> = rel.orEmpty().split(' ', ',').map(String::trim).filter(String::isNotBlank).toSet()
    private fun OpdsLink.isAcquisition(): Boolean = relTokens().any { it == "acquisition" || it.startsWith("http://opds-spec.org/acquisition") }
    private fun OpdsLink.isNavigation(): Boolean = relTokens().any { it in NAVIGATION_RELS }
    private fun OpdsLink.isCover(): Boolean = relTokens().any { it in COVER_RELS }

    private companion object {
        val NAVIGATION_RELS = setOf("alternate", "subsection", "collection", "http://opds-spec.org/sort/new", "http://opds-spec.org/sort/popular")
        val COVER_RELS = setOf("cover", "thumbnail", "http://opds-spec.org/image", "http://opds-spec.org/image/thumbnail")
    }
}
