package com.aibook.service.crawler;

import com.aibook.model.entity.CrawlerChapter;
import com.aibook.model.entity.CrawlerSite;
import com.aibook.model.entity.CrawlerSiteRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrawlerChapterDiagnosticsTest {
    private final ConfigBookCrawlerParser parser = new ConfigBookCrawlerParser(
            new BookContentCleaner(new ObjectMapper()));

    @Test
    void recognizesScriptOnlyAccessRestrictionWithoutAContentNodeAndRetainsHtml() {
        String html = """
                <!doctype html><html><head><title>提示信息</title></head><body>
                <script src='/public/static/cute-alert/cute-alert.js'></script>
                <script>
                  let code = parseInt("0");
                  let msg = "访问异常，请稍后再试，请于 2026-10-06 06:51:47 后再试";
                  cuteAlert({type:'error', title:msg, message:''});
                </script></body></html>
                """;
        CrawlerSiteRule rule = rule();
        rule.setSite(siteWithFailureMarker());
        CrawlerChapter chapter = chapter();

        assertThatThrownBy(() -> parse(html, chapter, rule))
                .hasMessage("源站响应命中失败特征：访问异常，请稍后再试");
        assertThat(chapter.getFailedResponseHtml()).isEqualTo(html);
    }

    @Test
    void recognizesVisibleMessagesButIgnoresCommentsAndUnrelatedScripts() {
        CrawlerSite site = siteWithFailureMarker();
        assertThat(CrawlerTaskService.matchedResponseFailureMarker(site,
                "<p>访问异常，请稍后再试</p>")).isNotNull();
        assertThat(CrawlerTaskService.matchedResponseFailureMarker(site,
                "<!--访问异常，请稍后再试--><script>const errors=['访问异常，请稍后再试'];</script>"))
                .isNull();
    }

    @Test
    void doesNotTreatPendingReleaseMarkersAsResponseFailures() {
        CrawlerSite site = CrawlerSite.builder().contentMarkersJson(
                "[{\"marker\":\"访问异常，请稍后再试\",\"status\":\"PENDING_RELEASE\"}]").build();
        assertThat(CrawlerTaskService.matchedResponseFailureMarker(site,
                "<p>访问异常，请稍后再试</p>")).isNull();
    }

    @Test
    void normalContentIsNotRejectedBecauseAnAlertScriptContainsTheMarker() {
        CrawlerSiteRule rule = rule();
        rule.setSite(siteWithFailureMarker());
        String html = "<div id='content'>正常正文</div><script>"
                + "let msg='访问异常，请稍后再试'; function onError(){cuteAlert({title:msg});}</script>";
        assertThat(parse(html, chapter(), rule).content()).isEqualTo("正常正文");
    }

    private CrawlerSite siteWithFailureMarker() {
        return CrawlerSite.builder().contentMarkersJson(
                "[{\"marker\":\"访问异常，请稍后再试\",\"status\":\"FAILED\"}]").build();
    }

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
