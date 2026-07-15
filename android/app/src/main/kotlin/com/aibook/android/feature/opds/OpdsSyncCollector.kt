package com.aibook.android.feature.opds

import com.aibook.android.core.network.opds.OpdsEntry
import com.aibook.android.core.network.opds.OpdsFeed

class OpdsSyncCollector(
    private val maxDepth: Int = DEFAULT_MAX_DEPTH,
    private val maxPagesPerCatalog: Int = DEFAULT_MAX_PAGES_PER_CATALOG,
    private val loadFeed: suspend (href: String?) -> OpdsFeed
) {
    suspend fun collect(): OpdsSyncCollection {
        val visitedPages = mutableSetOf<String>()
        val acquisitionEntries = linkedMapOf<String, OpdsEntry>()
        var catalogCount = 0

        suspend fun visit(href: String?, depth: Int) {
            if (depth > maxDepth) return
            var pageHref = href
            var pageCount = 0
            while (pageCount < maxPagesPerCatalog) {
                val pageKey = pageHref ?: "__root__"
                if (!visitedPages.add(pageKey)) break
                val feed = loadFeed(pageHref)
                feed.entries.filter { it.acquisitionLink != null }.forEach { entry ->
                    val key = entry.identifier ?: entry.acquisitionLink?.href ?: return@forEach
                    acquisitionEntries[key] = entry
                }
                for (entry in feed.entries.filter { it.acquisitionLink == null }) {
                    val childHref = entry.alternateLink?.href?.takeIf { it.isNotBlank() } ?: continue
                    if (childHref in visitedPages) continue
                    catalogCount += 1
                    visit(childHref, depth + 1)
                }
                pageHref = feed.nextLink?.href?.takeIf { it.isNotBlank() } ?: break
                pageCount += 1
            }
        }

        visit(href = null, depth = 0)
        return OpdsSyncCollection(
            acquisitionEntries = acquisitionEntries.values.toList(),
            catalogCount = catalogCount
        )
    }

    companion object {
        const val DEFAULT_MAX_DEPTH = 4
        const val DEFAULT_MAX_PAGES_PER_CATALOG = 100
    }
}

data class OpdsSyncCollection(
    val acquisitionEntries: List<OpdsEntry>,
    val catalogCount: Int
)
