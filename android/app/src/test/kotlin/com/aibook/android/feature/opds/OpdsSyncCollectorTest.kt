package com.aibook.android.feature.opds

import com.aibook.android.core.network.opds.OpdsEntry
import com.aibook.android.core.network.opds.OpdsFeed
import com.aibook.android.core.network.opds.OpdsLink
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OpdsSyncCollectorTest {

    @Test
    fun collectFollowsNestedCatalogsAndKeepsAcquisitionEntries() = runTest {
        val feeds = mapOf(
            null to OpdsFeed(
                title = "root",
                entries = listOf(
                    catalog("科幻", "/sci-fi"),
                    book("根目录书", "/root.epub")
                )
            ),
            "/sci-fi" to OpdsFeed(
                title = "sci-fi",
                entries = listOf(
                    catalog("经典", "/sci-fi/classics"),
                    book("三体", "/three-body.epub")
                )
            ),
            "/sci-fi/classics" to OpdsFeed(
                title = "classics",
                entries = listOf(book("基地", "/foundation.epub"))
            )
        )
        val collector = OpdsSyncCollector { href -> feeds.getValue(href) }

        val result = collector.collect()

        assertEquals(listOf("根目录书", "三体", "基地"), result.acquisitionEntries.map { it.title })
        assertEquals(2, result.catalogCount)
    }

    @Test
    fun collectSkipsVisitedCatalogsToAvoidCycles() = runTest {
        val feeds = mapOf(
            null to OpdsFeed("root", listOf(catalog("循环", "/loop"))),
            "/loop" to OpdsFeed("loop", listOf(catalog("回到循环", "/loop"), book("循环书", "/loop.epub")))
        )
        val collector = OpdsSyncCollector { href -> feeds.getValue(href) }

        val result = collector.collect()

        assertEquals(listOf("循环书"), result.acquisitionEntries.map { it.title })
        assertEquals(1, result.catalogCount)
    }

    private fun catalog(title: String, href: String) = OpdsEntry(
        title = title,
        alternateLink = OpdsLink(href = href)
    )

    private fun book(title: String, href: String) = OpdsEntry(
        title = title,
        acquisitionLink = OpdsLink(href = href, type = "application/epub+zip")
    )
}
