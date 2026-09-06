package com.aibook.service.crawler;

import com.aibook.dto.crawler.CrawlerDtos.*;
import com.aibook.model.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CrawlerRuleTestService {
    private final CrawlerManagementService managementService;
    private final CrawlerHttpClient httpClient;
    private final ConfigBookCrawlerParser parser;

    public RuleTestView test(User user, Long siteId, RuleTestRequest request) {
        return test(managementService.ownedSite(user, siteId), request);
    }

    RuleTestView test(CrawlerSite site, RuleTestRequest request) {
        long started = System.nanoTime();
        try {
            CrawlerSiteRule rule = request.rule() == null ? site.getRule() : detachedRule(request.rule());
            if (rule == null) throw new IllegalStateException("当前没有生效的采集规则");
            String url = httpClient.validateSiteUrl(site, request.url()).toString();
            CrawlerHttpClient.FetchResult detailResponse = httpClient.get(site, url);
            BookCrawlerParser.ParsedBook book = parser.parseBookDetail(detailResponse.html(), url, rule);
            CrawlerHttpClient.FetchResult listResponse = book.chapterListUrl().equals(url)
                    ? detailResponse : httpClient.get(site, book.chapterListUrl());
            List<BookCrawlerParser.ParsedChapter> chapters = parser.parseChapterList(
                    listResponse.html(), book.chapterListUrl(), rule);
            BookCrawlerParser.ParsedChapter sample = chapters.get(0);
            CrawlerHttpClient.FetchResult chapterResponse = httpClient.get(site, sample.url());
            BookCrawlerParser.ParsedContent content = parser.parseChapter(chapterResponse.html(), sample.url(), rule);
            return new RuleTestView(true, book.title(), book.author(), book.description(), book.coverUrl(),
                    book.status(), book.chapterListUrl(), chapters.size(), sample.title(), content.content().length(),
                    preview(content.content()), elapsed(started), null);
        } catch (Exception exception) {
            return new RuleTestView(false, null, null, null, null, null, null, 0,
                    null, 0, null, elapsed(started), message(exception));
        }
    }

    private CrawlerSiteRule detachedRule(RulePayload payload) {
        CrawlerSite holder = new CrawlerSite();
        managementService.applyRule(holder, payload);
        return holder.getRule();
    }
    private String preview(String value) { return value.length() <= 800 ? value : value.substring(0, 800) + "…"; }
    private long elapsed(long started) { return (System.nanoTime() - started) / 1_000_000; }
    private String message(Exception exception) { return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage(); }
}
