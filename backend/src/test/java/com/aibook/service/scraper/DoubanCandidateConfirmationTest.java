package com.aibook.service.scraper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aibook.model.entity.Book;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class DoubanCandidateConfirmationTest {
    private ScraperTrialService.TrialSnapshot finish(ScraperTrialService service, String id) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            var snapshot = service.get(id, 1L);
            if (snapshot.finished()) return snapshot;
            Thread.sleep(10);
        }
        throw new AssertionError("候选任务未完成");
    }

    @Test
    @SuppressWarnings("unchecked")
    void candidateSearchAndDetailDoNotWriteUntilExplicitConfirmation() throws Exception {
        var scraper = mock(DoubanScraper.class);
        when(scraper.getConfigKey()).thenReturn("douban");
        when(scraper.getName()).thenReturn("豆瓣读书");
        when(scraper.supports(any())).thenReturn(true);
        when(scraper.searchCandidates("三体")).thenReturn(List.of(
                new DoubanScraper.Candidate("123", "三体：黑暗森林", "刘慈欣", "2008", "", "")));
        var metadata = MetadataScraper.BookMetadata.builder().title("三体：黑暗森林").author("刘慈欣").build();
        when(scraper.getBookDetail("123")).thenReturn(metadata);
        var service = new ScraperTrialService(List.of(scraper));
        BiFunction<Long, MetadataScraper.BookMetadata, MetadataScrapingService.ScrapeResult> apply = mock(BiFunction.class);
        when(apply.apply(7L, metadata)).thenReturn(MetadataScrapingService.ScrapeResult.builder()
                .success(true).matched(true).updatedFields(List.of("author")).message("已补全作者").build());
        try {
            var book = new Book();
            book.setId(7L);
            book.setTitle("三体");
            String id = service.start("douban", book, 1L);
            var candidates = finish(service, id);
            assertThat(candidates.status()).isEqualTo("AWAITING_SELECTION");
            assertThat(candidates.candidates()).hasSize(1);
            assertThat(candidates.failure()).isNull();
            assertThat(candidates.metadata()).isNull();
            verify(scraper, never()).getBookDetail(anyString());
            assertThatThrownBy(() -> service.confirmCandidate(id, 1L, "123", apply))
                    .isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> service.selectCandidate(id, 2L, "123"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
            assertThatThrownBy(() -> service.selectCandidate(id, 1L, "999"))
                    .isInstanceOf(ResponseStatusException.class);
            service.selectCandidate(id, 1L, "123");
            var preview = finish(service, id);
            assertThat(preview.status()).isEqualTo("REVIEW_REQUIRED");
            assertThat(preview.metadata().getTitle()).isEqualTo("三体：黑暗森林");
            verifyNoInteractions(apply);
            assertThatThrownBy(() -> service.confirmCandidate(id, 2L, "123", apply))
                    .isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> service.confirmCandidate(id, 1L, "999", apply))
                    .isInstanceOf(ResponseStatusException.class);
            service.confirmCandidate(id, 1L, "123", apply);
            assertThat(service.get(id, 1L).applied()).isTrue();
            assertThat(service.get(id, 1L).updatedFields()).containsExactly("author");
            verify(apply, times(1)).apply(7L, metadata);
            assertThatThrownBy(() -> service.confirmCandidate(id, 1L, "123", apply))
                    .isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> service.selectCandidate(id, 1L, "123"))
                    .isInstanceOf(ResponseStatusException.class);
        } finally {
            service.shutdown();
        }
    }
}
