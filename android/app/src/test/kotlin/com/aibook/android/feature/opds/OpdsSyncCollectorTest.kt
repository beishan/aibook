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

    @Test
    fun collectStopsAtMaxDepth() = runTest {
        val feeds = mapOf(
            null to OpdsFeed("root", listOf(catalog("一层", "/one"))),
            "/one" to OpdsFeed("one", listOf(catalog("二层", "/two"))),
            "/two" to OpdsFeed("two", listOf(book("太深的书", "/deep.epub")))
        )
        val collector = OpdsSyncCollector(maxDepth = 1) { href -> feeds.getValue(href) }

        val result = collector.collect()

        assertEquals(emptyList(), result.acquisitionEntries.map { it.title })
        assertEquals(2, result.catalogCount)
    }

    @Test
    fun collectFollowsEveryNextPageAndDeduplicatesEntries() = runTest {
        val feeds = mapOf(
            null to OpdsFeed("page 1", listOf(book("一", "/one.epub")), OpdsLink("/page/2")),
            "/page/2" to OpdsFeed("page 2", listOf(book("一的重复", "/one.epub"), book("二", "/two.epub")), OpdsLink("/page/3")),
            "/page/3" to OpdsFeed("page 3", listOf(book("三", "/three.epub")))
        )

        val result = OpdsSyncCollector { href -> feeds.getValue(href) }.collect()

        assertEquals(listOf("一的重复", "二", "三"), result.acquisitionEntries.map { it.title })
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
