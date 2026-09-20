package com.aibook.service.crawler;

import com.aibook.model.entity.CrawlerSiteRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigBookCrawlerParserTest {
    private final ConfigBookCrawlerParser parser = new ConfigBookCrawlerParser(new BookContentCleaner(new ObjectMapper()));
    private final CrawlerSiteRule rule = CrawlerSiteRule.builder()
            .titleSelector("h1.title").authorSelector(".author").coverSelector("img.cover::data-src")
            .descriptionSelector(".intro").categorySelector(".category").tagsSelector(".tags a")
            .statusSelector(".status").chapterListUrlSelector("a.catalog")
            .chapterItemSelector("#chapters li").chapterTitleSelector(":scope").chapterUrlSelector("a")
            .contentTitleSelector("h1").contentSelector("#content").removeSelectors(".ad")
            .discoveryItemSelector(".books .book").discoveryUrlSelector("a.title")
            .discoveryTitleSelector("a.title").discoveryAuthorSelector(".writer")
            .discoveryCoverSelector("img::data-src").discoveryCategorySelector(".category")
            .discoveryLatestChapterSelector(".latest").discoveryNextPageSelector("a.next")
            .xpathRemoveSelectors("//span[@class='watermark']")
            .stringReplacementsJson("{\"下载APP\":\"\"}").removeBlankLines(true).saveOriginalHtml(true)
            .regexReplacementsJson("{\"example\\\\.com\":\"\"}").minChapterLength(10).build();

    @Test
    void parsesDiscoveryListAndNextPage() {
        String html = "<div class='books'><article class='book'><a class='title' href='/book/42'>山海记</a>"
                + "<span class='writer'>北山</span><span class='category'>奇幻</span>"
                + "<span class='latest'>第十章</span><img data-src='/cover/42.jpg'></article></div>"
                + "<a class='next' href='/books?page=2'>下一页</a>";
        var books = parser.parseBookList(html, "https://books.example.com/books", rule);
        assertEquals(1, books.size());
        assertEquals("山海记", books.get(0).title());
        assertEquals("https://books.example.com/book/42", books.get(0).url());
        assertEquals("https://books.example.com/cover/42.jpg", books.get(0).coverUrl());
        assertEquals("https://books.example.com/books?page=2",
                parser.parseNextBookListPage(html, "https://books.example.com/books", rule));
    }

    @Test
    void parsesBookMetadataAndResolvesUrls() {
        String html = "<h1 class='title'>山海记</h1><span class='author'>北山</span>"
                + "<img class='cover' data-src='/cover.jpg'><div class='intro'>简介</div>"
                + "<span class='category'>都市</span><span class='status'>连载中</span>"
                + "<div class='tags'><a>后宫</a><a>NTR</a><a>后宫</a></div>"
                + "<a class='catalog' href='/book/42/catalog'>目录</a>";
        BookCrawlerParser.ParsedBook book = parser.parseBookDetail(html, "https://books.example.com/book/42", rule);
        assertEquals("山海记", book.title());
        assertEquals("北山", book.author());
        assertEquals("https://books.example.com/cover.jpg", book.coverUrl());
        assertEquals("都市", book.category());
        assertEquals(List.of("后宫", "NTR"), book.tags());
        assertEquals("连载中", book.status());
        assertEquals("https://books.example.com/book/42/catalog", book.chapterListUrl());
    }

    @Test
    void parsesDefinitionListCategoryAndIndividualTagsWithoutDependingOnPositions() {
        CrawlerSiteRule positionalRule = CrawlerSiteRule.builder()
                .titleSelector("main h1").authorSelector("main dl:nth-of-type(1) dd")
                .categorySelector("main dl:nth-of-type(2) dd")
                .tagsSelector("main dl:nth-of-type(3) dd")
                .statusSelector("main dl:nth-of-type(2) dd")
                .chapterListUrlSelector("a.catalog").build();
        String html = "<main><h1>下运河风情</h1>"
                + "<dl><dt>作者</dt><dd>以泪洗面奶</dd></dl>"
                + "<dl><dt>状态</dt><dd>已完结</dd></dl>"
                + "<dl><dt>分类</dt><dd><a href='/cat/192/'>乡村</a></dd></dl>"
                + "<dl><dt>标签</dt><dd><a href='/tag/2128/'>乱交</a>"
                + "<a href='/tag/5024/'>村姑</a></dd></dl>"
                + "<a class='catalog' href='list/'>章节目录</a></main>";

        BookCrawlerParser.ParsedBook book = parser.parseBookDetail(
                html, "https://www.chunxiaoge.com/book/499184/", positionalRule);

        assertEquals("乡村", book.category());
        assertEquals(List.of("乱交", "村姑"), book.tags());
    }

    @Test
    void parsesChaptersAndCleansContent() {
        String catalog = "<ul id='chapters'><li><a href='/c/1.html'>第一章 风起</a></li><li><a href='/c/2.html'>第二章</a></li></ul>";
        var chapters = parser.parseChapterList(catalog, "https://books.example.com/book/42/catalog", rule);
        assertEquals(2, chapters.size());
        assertEquals("https://books.example.com/c/1.html", chapters.get(0).url());

        String page = "<h1>第一章 风起</h1><div id='content'><p>第一段 example.com 下载APP</p><div class='ad'>广告</div><span class='watermark'>水印</span><p>第二段</p></div>";
        var content = parser.parseChapter(page, chapters.get(0).url(), rule);
        assertFalse(content.content().contains("广告"));
        assertFalse(content.content().contains("example.com"));
        assertFalse(content.content().contains("下载APP"));
        assertFalse(content.content().contains("水印"));
        assertTrue(content.content().contains("第一段"));
        assertTrue(content.content().contains("第二段"));
        assertTrue(content.originalHtml().contains("watermark"));
    }

    @Test
    void rejectsEmptyChapterResult() {
        assertThrows(IllegalArgumentException.class,
                () -> parser.parseChapterList("<html></html>", "https://books.example.com/book/42", rule));
    }
}
