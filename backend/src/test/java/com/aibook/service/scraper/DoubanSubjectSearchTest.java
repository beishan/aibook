package com.aibook.service.scraper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URL;
import java.util.List;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DoubanSubjectSearchTest {
    private static final String SEARCH_PAGE = """
            <html><title>凡人修仙传 - 读书 - 豆瓣搜索</title><script>
            window.__DATA__ = {"count":4,"error_info":"","total":38,"items":[
              {"id":7968,"title":"[丛书] 凡人修仙传","url":"https://book.douban.com/series/7968"},
              {"tpl_name":"search_more","urls":[{"url":"https://book.douban.com/series/search?q=test"}]},
              {"id":4192766,"tpl_name":"search_subject","title":"凡人修仙传（1-10）",
               "url":"https://book.douban.com/subject/4192766/",
               "abstract":"忘语 / 太白文艺出版社 / 2010-01 / 280.00元（全10册）",
               "cover_url":"https://img3.doubanio.com/view/subject/m/public/s4126779.jpg"},
              {"id":5027280,"tpl_name":"search_subject","title":"凡人修仙传（11-20）",
               "url":"https://book.douban.com/subject/5027280/","abstract":"忘语 / 太白文艺出版社 / 2010-12"}
            ]}; window.unrelated = 'not executed';
            </script></html>
            """;

    @Test
    void readsInlineSearchDataAndSkipsSeriesAndNavigationEntries() {
        var candidates = new DoubanScraper().parseSearchCandidates(Jsoup.parse(SEARCH_PAGE));
        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).id()).isEqualTo("4192766");
        assertThat(candidates.get(0).title()).isEqualTo("凡人修仙传（1-10）");
        assertThat(candidates.get(0).author()).isEqualTo("忘语");
        assertThat(candidates.get(0).publishDate()).isEqualTo("2010-01");
        assertThat(candidates.get(1).id()).isEqualTo("5027280");
    }

    @Test
    void usesProvidedBookSearchEntryAndDoesNotDependOnSuggestWhenSearchHasCandidates() throws Exception {
        var scraper = spy(new DoubanScraper());
        String url = "https://search.douban.com/book/subject_search?search_text=%E5%87%A1%E4%BA%BA%E4%BF%AE%E4%BB%99%E4%BC%A0&cat=1001";
        Connection connection = mock(Connection.class, RETURNS_SELF);
        Connection.Response response = mock(Connection.Response.class);
        doReturn(connection).when(scraper).createConnection(url);
        when(connection.execute()).thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.url()).thenReturn(new URL(url));
        when(response.body()).thenReturn(SEARCH_PAGE);
        try (var trace = ScrapeDiagnostics.open(message -> {})) {
            List<DoubanScraper.Candidate> candidates = scraper.searchCandidates("凡人修仙传");
            assertThat(candidates.get(0).id()).isEqualTo("4192766");
            assertThat(trace.failures()).isEmpty();
            verify(scraper, times(1)).createConnection(anyString());
            verify(connection).header("Accept-Encoding", "gzip, deflate");
        }
    }

    @Test
    void zeroResultsAndUnsupportedPageDataRemainDifferentFailures() {
        String[] payloads = {"{\"total\":0,\"items\":[]}", "\"opaque-page-data\"", "{broken"};
        ScrapeDiagnostics.Code[] codes = {ScrapeDiagnostics.Code.NO_MATCH,
                ScrapeDiagnostics.Code.UNRECOGNIZED_RESPONSE, ScrapeDiagnostics.Code.PARSE_ERROR};
        for (int index = 0; index < payloads.length; index++) {
            try (var trace = ScrapeDiagnostics.open(message -> {})) {
                var candidates = new DoubanScraper().parseSearchCandidates(Jsoup.parse(
                        "<script>window.__DATA__ = " + payloads[index] + ";</script>"));
                assertThat(candidates).isEmpty();
                assertThat(trace.primaryFailure().code()).isEqualTo(codes[index]);
            }
        }
    }

    @Test
    void renderedNewLayoutRemainsSupportedAndExternalUrlsAreExcluded() {
        var candidates = new DoubanScraper().parseSearchCandidates(Jsoup.parse("""
                <div class='item-root'><a class='title' href='https://book.douban.com/subject/4192766/'>凡人修仙传（1-10）</a>
                  <div class='abstract'>忘语 / 太白文艺出版社 / 2010-01</div></div>
                <a href='https://evil.example/book.douban.com/subject/4192766/'>外站</a>
                """));
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).author()).contains("忘语");
    }

    @Test
    void detailFieldsAcceptColonOutsideLabelAndPreferFullIntroduction() {
        var document = Jsoup.parse("""
                <h1><span property='v:itemreviewed'>凡人修仙传（1-10）</span></h1>
                <div id='info'><span><span class='pl'> 作者</span>: <a>忘语</a></span><br>
                <span class='pl'>出版社:</span><a>太白文艺出版社</a><br>
                <span class='pl'>出版年：</span>2010-01<br>
                <span class='pl'>ISBN:</span>9787806807583<br></div>
                <div id='link-report'><span class='short'><div class='intro'><p>简短节选……</p></div></span>
                <span class='all hidden'><div class='intro'><p>完整简介第一段。</p><p>完整简介第二段。</p></div></span></div>
                """);
        MetadataScraper.BookMetadata metadata = ReflectionTestUtils.invokeMethod(
                new DoubanScraper(), "parseBookPage", document, "4192766");
        assertThat(metadata.getTitle()).isEqualTo("凡人修仙传（1-10）");
        assertThat(metadata.getAuthor()).isEqualTo("忘语");
        assertThat(metadata.getPublisher()).isEqualTo("太白文艺出版社");
        assertThat(metadata.getPublishDate()).isEqualTo("2010-01");
        assertThat(metadata.getIsbn()).isEqualTo("9787806807583");
        assertThat(metadata.getDescription()).isEqualTo("完整简介第一段。\n完整简介第二段。");
    }
}
