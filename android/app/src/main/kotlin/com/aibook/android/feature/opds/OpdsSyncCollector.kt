package com.aibook.android.feature.opds

import com.aibook.android.core.network.opds.OpdsEntry
import com.aibook.android.core.network.opds.OpdsFeed

class OpdsSyncCollector(
    private val maxDepth: Int = DEFAULT_MAX_DEPTH,
    private val loadFeed: suspend (href: String?) -> OpdsFeed
) {
    suspend fun collect(): OpdsSyncCollection {
        val visitedCatalogs = mutableSetOf<String>()
        val acquisitionEntries = mutableListOf<OpdsEntry>()
        var catalogCount = 0

        suspend fun visit(href: String?, depth: Int) {
            if (depth > maxDepth) return
            val feed = loadFeed(href)
            acquisitionEntries += feed.entries.filter { it.acquisitionLink != null }

            for (entry in feed.entries.filter { it.acquisitionLink == null }) {
                val childHref = entry.alternateLink?.href?.takeIf { it.isNotBlank() } ?: continue
                if (!visitedCatalogs.add(childHref)) continue
                catalogCount += 1
                visit(childHref, depth + 1)
            }
        }

        visit(href = null, depth = 0)
        return OpdsSyncCollection(
            acquisitionEntries = acquisitionEntries,
            catalogCount = catalogCount
        )
    }

    companion object {
        const val DEFAULT_MAX_DEPTH = 4
    }
}

data class OpdsSyncCollection(
    val acquisitionEntries: List<OpdsEntry>,
    val catalogCount: Int
)
