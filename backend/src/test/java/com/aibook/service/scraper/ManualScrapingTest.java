package com.aibook.service.scraper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aibook.model.entity.Book;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ManualScrapingTest {
    private ScraperTrialService.TrialSnapshot finish(ScraperTrialService service, String id) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            var result = service.getManual(id, 1L);
            if (result.finished()) return result;
            Thread.sleep(10);
        }
        throw new AssertionError("手动查询未完成");
    }

    private Book book() {
        return Book.builder().id(7L).title("测试书籍").build();
    }

    private MetadataScrapingService.ScrapeResult success() {
        return MetadataScrapingService.ScrapeResult.builder().success(true)
                .updatedFields(List.of("description")).message("已保存").build();
    }

    @Test
    void singleSourceRequiresExplicitOwnedConfirmationAndPreservesSourceAttribution() throws Exception {
        var scraper = mock(MetadataScraper.class);
        when(scraper.getConfigKey()).thenReturn("jd");
        when(scraper.getName()).thenReturn("京东读书");
        when(scraper.supports(any())).thenReturn(true);
        when(scraper.scrape(any())).thenReturn(MetadataScraper.BookMetadata.builder()
                .title("候选书籍").description("简介").build());
        var service = new ScraperTrialService(List.of(scraper));
        AtomicInteger writes = new AtomicInteger();
        ScraperTrialService.ManualApply apply = (bookId, metadata, source) -> {
            assertThat(bookId).isEqualTo(7L);
            assertThat(metadata.getTitle()).isEqualTo("候选书籍");
            assertThat(source).isEqualTo("京东读书");
            writes.incrementAndGet();
            return success();
        };
        try {
            String id = service.startManual("jd", book(), 1L);
            assertThat(finish(service, id).status()).isEqualTo("MATCHED");
            assertThat(writes).hasValue(0);
            assertThatThrownBy(() -> service.getManual(id, 2L)).isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> service.confirmManual(id, 2L, apply)).isInstanceOf(ResponseStatusException.class);
            service.confirmManual(id, 1L, apply);
            assertThat(service.getManual(id, 1L).status()).isEqualTo("APPLIED");
            assertThat(writes).hasValue(1);
            assertThatThrownBy(() -> service.confirmManual(id, 1L, apply)).isInstanceOf(ResponseStatusException.class);
            assertThat(writes).hasValue(1);
            String trial = service.start("jd", book(), 1L);
            assertThatThrownBy(() -> service.getManual(trial, 1L)).isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> service.confirmManual(trial, 1L, apply)).isInstanceOf(ResponseStatusException.class);
        } finally {
            service.shutdown();
        }
    }

    @Test
    void doubanRequiresCandidateDetailAndCannotApplyUnselectedSearchResults() throws Exception {
        var scraper = mock(DoubanScraper.class);
        when(scraper.getConfigKey()).thenReturn("douban");
        when(scraper.getName()).thenReturn("豆瓣读书");
        when(scraper.supports(any())).thenReturn(true);
        when(scraper.searchCandidates(any())).thenReturn(List.of(
                new DoubanScraper.Candidate("123", "候选", "作者", "2024", "", "")));
        when(scraper.getBookDetail("123")).thenReturn(
                MetadataScraper.BookMetadata.builder().title("候选").build());
        var service = new ScraperTrialService(List.of(scraper));
        AtomicInteger writes = new AtomicInteger();
        ScraperTrialService.ManualApply apply = (id, metadata, source) -> {
            writes.incrementAndGet();
            return success();
        };
        try {
            String id = service.startManual("douban", book(), 1L);
            assertThat(finish(service, id).status()).isEqualTo("AWAITING_SELECTION");
            assertThatThrownBy(() -> service.confirmManual(id, 1L, apply)).isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> service.selectManualCandidate(id, 1L, "999")).isInstanceOf(ResponseStatusException.class);
            service.selectManualCandidate(id, 1L, "123");
            assertThat(finish(service, id).status()).isEqualTo("REVIEW_REQUIRED");
            assertThat(writes).hasValue(0);
            service.confirmManual(id, 1L, apply);
            assertThat(writes).hasValue(1);
        } finally {
            service.shutdown();
        }
    }

    @Test
    void missingMetadataCannotBeSaved() throws Exception {
        var scraper = mock(MetadataScraper.class);
        when(scraper.getConfigKey()).thenReturn("jd");
        when(scraper.getName()).thenReturn("京东读书");
        when(scraper.supports(any())).thenReturn(true);
        var service = new ScraperTrialService(List.of(scraper));
        try {
            String id = service.startManual("jd", book(), 1L);
            assertThat(finish(service, id).status()).isEqualTo("NO_RESULT");
            assertThatThrownBy(() -> service.confirmManual(id, 1L, (bookId, metadata, source) -> {
                throw new AssertionError("无结果时不得保存");
            })).isInstanceOf(ResponseStatusException.class);
        } finally {
            service.shutdown();
        }
    }
}
