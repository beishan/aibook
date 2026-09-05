package com.aibook.service.crawler;

import com.aibook.model.entity.CrawlerSite;
import com.aibook.model.entity.CrawlerSiteRule;
import java.util.List;

public interface BookCrawlerParser {
    List<ParsedDiscovery> parseBookList(String html, String pageUrl, CrawlerSiteRule rule);
    String parseNextBookListPage(String html, String pageUrl, CrawlerSiteRule rule);
    ParsedBook parseBookDetail(String html, String pageUrl, CrawlerSiteRule rule);
    List<ParsedChapter> parseChapterList(String html, String pageUrl, CrawlerSiteRule rule);
    ParsedContent parseChapter(String html, String pageUrl, CrawlerSiteRule rule);
    default boolean supports(CrawlerSite site) { return false; }

    record ParsedBook(String externalId, String title, String author, String coverUrl,
            String description, String category, String status, String latestChapter,
            String chapterListUrl) { }
    record ParsedChapter(String externalId, int index, String title, String url) { }
    record ParsedContent(String title, String content) { }
    record ParsedDiscovery(String externalId, String title, String author, String coverUrl,
            String category, String latestChapter, String url) { }
}
