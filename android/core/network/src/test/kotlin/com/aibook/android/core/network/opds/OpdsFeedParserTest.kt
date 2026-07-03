package com.aibook.android.core.network.opds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OpdsFeedParserTest {
    @Test
    fun `parses atom opds feed entries and acquisition links`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>汗牛充栋书库</title>
              <entry>
                <title>三体</title>
                <author><name>刘慈欣</name></author>
                <summary>地球文明收到来自三体文明的信息。</summary>
                <link rel="http://opds-spec.org/acquisition" href="/opds/books/1/download" type="application/epub+zip" />
              </entry>
            </feed>
        """.trimIndent()

        val feed = OpdsFeedParser().parse(xml)

        assertEquals("汗牛充栋书库", feed.title)
        assertEquals(1, feed.entries.size)
        assertEquals("三体", feed.entries.single().title)
        assertEquals("刘慈欣", feed.entries.single().author)
        assertEquals("地球文明收到来自三体文明的信息。", feed.entries.single().summary)
        assertNotNull(feed.entries.single().acquisitionLink)
        assertEquals("/opds/books/1/download", feed.entries.single().acquisitionLink?.href)
        assertEquals("application/epub+zip", feed.entries.single().acquisitionLink?.type)
    }

    @Test
    fun `returns empty feed for blank xml`() {
        val feed = OpdsFeedParser().parse("")

        assertEquals("", feed.title)
        assertEquals(emptyList(), feed.entries)
    }

    @Test
    fun `treats subsection links as navigation links`() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>目录</title>
              <entry>
                <title>科幻</title>
                <link rel="subsection" href="/opds/categories/scifi" type="application/atom+xml" />
              </entry>
            </feed>
        """.trimIndent()

        val feed = OpdsFeedParser().parse(xml)

        assertEquals("/opds/categories/scifi", feed.entries.single().alternateLink?.href)
    }
}
