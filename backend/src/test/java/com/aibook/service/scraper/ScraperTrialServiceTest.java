package com.aibook.service.scraper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aibook.model.entity.Book;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ScraperTrialServiceTest {
    private final MetadataScraper scraper = mock(MetadataScraper.class);
    private final ScraperTrialService service = new ScraperTrialService(List.of(scraper));

    private Book book() {
        when(scraper.getConfigKey()).thenReturn("test");
        when(scraper.getName()).thenReturn("测试来源");
        when(scraper.supports(any())).thenReturn(true);
        Book book = new Book();
        book.setTitle("三体");
        book.setIsbn("123");
        return book;
    }

    @AfterEach
    void close() {
        service.shutdown();
    }

    private ScraperTrialService.TrialSnapshot finish(String id) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            var snapshot = service.get(id, 1L);
            if (snapshot.finished()) return snapshot;
            Thread.sleep(10);
        }
        throw new AssertionError("试刮削没有完成");
    }

    @Test
    void fallsBackToTitleAndNeverMutatesOriginal() throws Exception {
        Book original = book();
        when(scraper.scrapeByIsbn("123")).thenReturn(null);
        when(scraper.scrape(any())).thenAnswer(invocation -> {
            Book query = invocation.getArgument(0);
            assertThat(query.getIsbn()).isNull();
            assertThat(query).isNotSameAs(original);
            return MetadataScraper.BookMetadata.builder().title("三体").author("刘慈欣").build();
        });
        String id = service.start("test", original, 1L);
        assertThatThrownBy(() -> service.get(id, 2L)).isInstanceOf(ResponseStatusException.class);
        var result = finish(id);
        assertThat(result.status()).isEqualTo("MATCHED");
        assertThat(result.metadata().getAuthor()).isEqualTo("刘慈欣");
        assertThat(result.logs()).extracting(ScraperTrialService.TrialLog::message)
                .anyMatch(message -> message.contains("ISBN"))
                .anyMatch(message -> message.contains("按书名"));
        assertThat(original.getIsbn()).isEqualTo("123");
    }

    @Test
    void isbnHitDoesNotIssueTitleQuery() throws Exception {
        Book original = book();
        when(scraper.scrapeByIsbn("123")).thenReturn(
                MetadataScraper.BookMetadata.builder().title("三体").build());
        assertThat(finish(service.start("test", original, 1L)).status()).isEqualTo("MATCHED");
        verify(scraper, never()).scrape(any());
    }

    @Test
    void noResultIsNotReportedAsAvailable() throws Exception {
        Book original = book();
        when(scraper.scrape(any())).thenReturn(MetadataScraper.BookMetadata.builder().build());
        var result = finish(service.start("test", original, 1L));
        assertThat(result.status()).isEqualTo("NO_RESULT");
        assertThat(result.metadata()).isNull();
        assertThat(result.logs().getLast().message()).contains("不能据此断定配置可用");
    }

    @Test
    void unsupportedDoesNotRequestRemote() throws Exception {
        Book original = book();
        when(scraper.supports(any())).thenReturn(false);
        assertThat(finish(service.start("test", original, 1L)).status()).isEqualTo("UNSUPPORTED");
        verify(scraper, never()).scrapeByIsbn(any());
        verify(scraper, never()).scrape(any());
    }

    @Test
    void errorsDoNotLeakCredentials() throws Exception {
        Book original = book();
        when(scraper.scrapeByIsbn(any())).thenThrow(new IllegalStateException("secret-api-key"));
        var result = finish(service.start("test", original, 1L));
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.logs().toString()).doesNotContain("secret-api-key");
    }

    @Test
    void keepsAccessRestrictionWhenFallbackSearchReturnsNoMatch() throws Exception {
        Book original = book();
        when(scraper.scrapeByIsbn(any())).thenAnswer(invocation -> {
            ScrapeDiagnostics.request("https://example.com/search?key=secret");
            ScrapeDiagnostics.response(403, "https://example.com/search", "forbidden");
            return null;
        });
        when(scraper.scrape(any())).thenAnswer(invocation -> {
            ScrapeDiagnostics.report(ScrapeDiagnostics.Code.NO_MATCH, "书名没有匹配结果");
            return null;
        });
        var result = finish(service.start("test", original, 1L));
        assertThat(result.status()).isEqualTo("NO_RESULT");
        assertThat(result.failure().code()).isEqualTo(ScrapeDiagnostics.Code.ACCESS_DENIED);
        assertThat(result.diagnostics()).hasSize(2);
        assertThat(result.logs().toString() + result.diagnostics()).doesNotContain("secret");
    }

    @Test
    void fallbackSuccessRetainsDiagnosticsWithoutReportingOverallFailure() throws Exception {
        Book original = book();
        when(scraper.scrapeByIsbn(any())).thenAnswer(invocation -> {
            ScrapeDiagnostics.report(ScrapeDiagnostics.Code.NO_MATCH, "ISBN 没有匹配结果");
            return null;
        });
        when(scraper.scrape(any())).thenReturn(MetadataScraper.BookMetadata.builder().title("三体").build());
        var result = finish(service.start("test", original, 1L));
        assertThat(result.status()).isEqualTo("MATCHED");
        assertThat(result.failure()).isNull();
        assertThat(result.diagnostics()).hasSize(1);
    }

    @Test
    void rejectsDuplicateActiveTaskAndUnknownSource() throws Exception {
        Book original = book();
        CountDownLatch release = new CountDownLatch(1);
        when(scraper.scrapeByIsbn(any())).thenAnswer(invocation -> {
            release.await(3, TimeUnit.SECONDS);
            return null;
        });
        try {
            service.start("test", original, 1L);
            assertThatThrownBy(() -> service.start("test", original, 1L))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
            assertThatThrownBy(() -> service.start("unknown", original, 2L))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        } finally {
            release.countDown();
        }
    }
}
