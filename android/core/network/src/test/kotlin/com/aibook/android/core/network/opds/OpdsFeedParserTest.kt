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
    fun `parses next page link from feed`() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>分页书库</title>
              <link rel="self" href="/opds?page=1" />
              <link rel="next" href="/opds?page=2" type="application/atom+xml" />
            </feed>
        """.trimIndent()

        val feed = OpdsFeedParser().parse(xml)

        assertEquals("/opds?page=2", feed.nextLink?.href)
        assertEquals("application/atom+xml", feed.nextLink?.type)
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

    @Test
    fun `uses content text when summary is absent and parses thumbnail cover`() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>书库</title>
              <entry>
                <title>基地</title>
                <content type="text">银河帝国衰亡史。</content>
                <link rel="http://opds-spec.org/image/thumbnail" href="/covers/foundation.jpg" type="image/jpeg" />
              </entry>
            </feed>
        """.trimIndent()

        val feed = OpdsFeedParser().parse(xml)

        assertEquals("银河帝国衰亡史。", feed.entries.single().summary)
        assertEquals("/covers/foundation.jpg", feed.entries.single().coverLink?.href)
    }

    @Test
    fun `prefers open access acquisition links`() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>书库</title>
              <entry>
                <title>三体</title>
                <link rel="alternate" href="/books/1" />
                <link rel="http://opds-spec.org/acquisition/open-access" href="/opds/books/1/download" type="application/epub+zip" />
              </entry>
            </feed>
        """.trimIndent()

        val feed = OpdsFeedParser().parse(xml)

        assertEquals("/opds/books/1/download", feed.entries.single().acquisitionLink?.href)
        assertEquals("/books/1", feed.entries.single().alternateLink?.href)
    }

    @Test
    fun `parses opds 2 json publications navigation groups and cursor`() {
        val payload = """
            {
              "metadata": {"title": "JSON 书库"},
              "links": [{"rel": "next", "href": "/opds?page=cursor-2", "type": "application/opds+json"}],
              "publications": [{
                "metadata": {
                  "identifier": "urn:isbn:9780000000001",
                  "title": "三体",
                  "author": [{"name": "刘慈欣"}],
                  "description": "科幻小说",
                  "subject": [{"name": "科幻"}],
                  "modified": "2026-07-15T00:00:00Z"
                },
                "links": [
                  {"rel": "http://opds-spec.org/acquisition/open-access", "href": "/books/1.epub", "type": "application/epub+zip"},
                  {"rel": ["cover", "thumbnail"], "href": "/covers/1.jpg", "type": "image/jpeg"}
                ]
              }],
              "groups": [{
                "metadata": {"title": "热门"},
                "navigation": [{"title": "更多热门", "href": "/popular", "type": "application/opds+json"}]
              }]
            }
        """.trimIndent()

        val feed = OpdsFeedParser().parse(payload)

        assertEquals("JSON 书库", feed.title)
        assertEquals("/opds?page=cursor-2", feed.nextLink?.href)
        assertEquals("urn:isbn:9780000000001", feed.entries.first().identifier)
        assertEquals("刘慈欣", feed.entries.first().author)
        assertEquals(listOf("科幻"), feed.entries.first().categories)
        assertEquals("/covers/1.jpg", feed.entries.first().coverLink?.href)
        assertEquals("/popular", feed.entries.last().alternateLink?.href)
    }
}
