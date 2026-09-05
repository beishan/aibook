package com.aibook.service.crawler;

import com.aibook.model.entity.CrawlerSiteRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigBookCrawlerParserTest {
    private final ConfigBookCrawlerParser parser = new ConfigBookCrawlerParser(new ObjectMapper());
    private final CrawlerSiteRule rule = CrawlerSiteRule.builder()
            .titleSelector("h1.title").authorSelector(".author").coverSelector("img.cover::data-src")
            .descriptionSelector(".intro").chapterListUrlSelector("a.catalog")
            .chapterItemSelector("#chapters li").chapterTitleSelector(":scope").chapterUrlSelector("a")
            .contentTitleSelector("h1").contentSelector("#content").removeSelectors(".ad")
            .regexReplacementsJson("{\"example\\\\.com\":\"\"}").minChapterLength(10).build();

    @Test
    void parsesBookMetadataAndResolvesUrls() {
        String html = "<h1 class='title'>山海记</h1><span class='author'>北山</span>"
                + "<img class='cover' data-src='/cover.jpg'><div class='intro'>简介</div>"
                + "<a class='catalog' href='/book/42/catalog'>目录</a>";
        BookCrawlerParser.ParsedBook book = parser.parseBookDetail(html, "https://books.example.com/book/42", rule);
        assertEquals("山海记", book.title());
        assertEquals("北山", book.author());
        assertEquals("https://books.example.com/cover.jpg", book.coverUrl());
        assertEquals("https://books.example.com/book/42/catalog", book.chapterListUrl());
    }

    @Test
    void parsesChaptersAndCleansContent() {
        String catalog = "<ul id='chapters'><li><a href='/c/1.html'>第一章 风起</a></li><li><a href='/c/2.html'>第二章</a></li></ul>";
        var chapters = parser.parseChapterList(catalog, "https://books.example.com/book/42/catalog", rule);
        assertEquals(2, chapters.size());
        assertEquals("https://books.example.com/c/1.html", chapters.get(0).url());

        String page = "<h1>第一章 风起</h1><div id='content'><p>第一段 example.com</p><div class='ad'>广告</div><p>第二段</p></div>";
        var content = parser.parseChapter(page, chapters.get(0).url(), rule);
        assertFalse(content.content().contains("广告"));
        assertFalse(content.content().contains("example.com"));
        assertTrue(content.content().contains("第一段"));
        assertTrue(content.content().contains("第二段"));
    }

    @Test
    void rejectsEmptyChapterResult() {
        assertThrows(IllegalArgumentException.class,
                () -> parser.parseChapterList("<html></html>", "https://books.example.com/book/42", rule));
    }
}
