package com.aibook.service.crawler;

import com.aibook.model.entity.CrawlerChapter;
import com.aibook.model.entity.CrawlerSiteRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrawlerChapterDiagnosticsTest {
    private final ConfigBookCrawlerParser parser = new ConfigBookCrawlerParser(
            new BookContentCleaner(new ObjectMapper()));

    @Test
    void missingSelectorPreservesCompleteFetchedPageAndPreviousContent() {
        String html = "<!DOCTYPE html><html><head><title>登录</title></head>"
                + "<body><form>请登录</form><script>window.test = 1;</script></body></html>";
        CrawlerChapter chapter = chapter();
        chapter.setContent("旧正文");
        chapter.setOriginalHtml("<p>旧正文</p>");

        assertThatThrownBy(() -> parse(html, chapter, rule()))
                .hasMessageContaining("正文解析结果为空");

        assertThat(chapter.getFailedResponseHtml()).isEqualTo(html);
        assertThat(chapter.getFailedResponseTime()).isNotNull();
        assertThat(chapter.getFailedResponseHttpStatus()).isEqualTo(200);
        assertThat(chapter.getContent()).isEqualTo("旧正文");
        assertThat(chapter.getOriginalHtml()).isEqualTo("<p>旧正文</p>");
    }

    @Test
    void cleaningAwayAllTextAlsoCapturesResponse() {
        String html = "<html><body><div id='content'><p>正文</p></div></body></html>";
        CrawlerSiteRule rule = rule();
        rule.setRemoveSelectors("p");
        CrawlerChapter chapter = chapter();

        assertThatThrownBy(() -> parse(html, chapter, rule))
                .hasMessageContaining("正文清洗结果为空");
        assertThat(chapter.getFailedResponseHtml()).isEqualTo(html);
    }

    @Test
    void successfulRetryClearsOldFailureSnapshot() {
        CrawlerChapter chapter = chapter();
        assertThatThrownBy(() -> parse("<html>错误页面</html>", chapter, rule()));

        var content = parse("<div id='content'><p>有效正文</p></div>", chapter, rule());

        assertThat(content.content()).isEqualTo("有效正文");
        assertThat(chapter.getFailedResponseHtml()).isNull();
        assertThat(chapter.getFailedResponseTime()).isNull();
        assertThat(chapter.getFailedResponseHttpStatus()).isNull();
    }

    private BookCrawlerParser.ParsedContent parse(String html, CrawlerChapter chapter,
            CrawlerSiteRule rule) {
        return CrawlerTaskService.parseChapterWithDiagnostics(parser,
                new CrawlerHttpClient.FetchResult(html, 200, 10, null, null), chapter, rule);
    }

    private CrawlerChapter chapter() {
        return CrawlerChapter.builder().chapterUrl("https://example.com/chapter/1").build();
    }

    private CrawlerSiteRule rule() {
        return CrawlerSiteRule.builder().contentSelector("#content").build();
    }
}
