package com.aibook.service.scraper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.aibook.service.SystemConfigService;
import java.net.URL;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class ScraperSourceDiagnosticsTest {
    @Test
    void googleEmptyResultAndHttpErrorCarryDifferentReasons() {
        var config = mock(SystemConfigService.class);
        when(config.getConfig("scraper.google.api-key", null)).thenReturn("secret-key");
        var scraper = new GoogleBooksScraper(config);
        var server = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(scraper, "restTemplate")).build();
        server.expect(anything()).andRespond(withSuccess("{\"totalItems\":0}", MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        try (var trace = ScrapeDiagnostics.open(message -> {})) {
            assertThat(scraper.scrapeByIsbn("123")).isNull();
            assertThat(trace.primaryFailure().code()).isEqualTo(ScrapeDiagnostics.Code.NO_MATCH);
            trace.stage("书名查询");
            assertThat(scraper.searchByTitle("三体")).isNull();
            assertThat(trace.primaryFailure().code()).isEqualTo(ScrapeDiagnostics.Code.RATE_LIMITED);
            assertThat(trace.failures().toString()).doesNotContain("secret-key");
        }
        server.verify();
    }

    @Test
    void openLibrarySeparatesEmptyArrayUnexpectedSchemaAndMalformedJson() {
        var scraper = new OpenLibraryScraper();
        var server = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(scraper, "restTemplate")).build();
        server.expect(anything()).andRespond(withSuccess("{\"docs\":[]}", MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withSuccess("{\"unexpected\":true}", MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withSuccess("{not-json", MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withStatus(HttpStatus.NO_CONTENT));
        ScrapeDiagnostics.Code[] expected = {ScrapeDiagnostics.Code.NO_MATCH,
                ScrapeDiagnostics.Code.UNRECOGNIZED_RESPONSE, ScrapeDiagnostics.Code.PARSE_ERROR,
                ScrapeDiagnostics.Code.UNRECOGNIZED_RESPONSE};
        for (var code : expected) {
            try (var trace = ScrapeDiagnostics.open(message -> {})) {
                assertThat(scraper.searchByTitle("三体")).isNull();
                assertThat(trace.primaryFailure().code()).isEqualTo(code);
            }
        }
        server.verify();
    }

    @Test
    void htmlSourcesForwardHttp403AndHttp200ChallengesIntoTrialDiagnostics() throws Exception {
        for (Object scraper : new Object[] {spy(new DoubanScraper()), spy(new JdBookScraper())}) {
            for (int status : new int[] {403, 200}) {
                Connection connection = mock(Connection.class, RETURNS_SELF);
                Connection.Response response = mock(Connection.Response.class);
                when(connection.execute()).thenReturn(response);
                when(response.statusCode()).thenReturn(status);
                when(response.url()).thenReturn(new URL("https://example.com/search"));
                when(response.body()).thenReturn(status == 403 ? "forbidden"
                        : "<html><title>验证码安全验证</title></html>");
                if (scraper instanceof DoubanScraper douban) {
                    doReturn(connection).when(douban).createConnection("https://example.com/search");
                } else {
                    doReturn(connection).when((JdBookScraper) scraper).createConnection("https://example.com/search");
                }
                try (var trace = ScrapeDiagnostics.open(message -> {})) {
                    String result = ReflectionTestUtils.invokeMethod(scraper, "makeRequest", "https://example.com/search");
                    assertThat(result).isNull();
                    assertThat(trace.primaryFailure().code()).isEqualTo(status == 403
                            ? ScrapeDiagnostics.Code.ACCESS_DENIED : ScrapeDiagnostics.Code.BLOCKED);
                }
            }
        }
    }

    @Test
    void missingBookDetailsMustNotBecomeLanguageOnlySuccess() {
        for (Object scraper : new Object[] {new DoubanScraper(), new JdBookScraper()}) {
            try (var trace = ScrapeDiagnostics.open(message -> {})) {
                MetadataScraper.BookMetadata result = ReflectionTestUtils.invokeMethod(scraper,
                        "parseBookPage", Jsoup.parse("<html><title>未知页面</title></html>"), "123");
                assertThat(result).isNull();
                assertThat(trace.primaryFailure().code()).isEqualTo(ScrapeDiagnostics.Code.UNRECOGNIZED_RESPONSE);
            }
        }
    }
}
