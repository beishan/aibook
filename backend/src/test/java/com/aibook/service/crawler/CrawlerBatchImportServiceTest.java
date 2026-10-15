package com.aibook.service.crawler;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aibook.dto.crawler.CrawlerBatchImportDtos.JobView;
import com.aibook.model.entity.User;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CrawlerBatchImportServiceTest {
    @Test
    void deduplicatesBatchAndContinuesAfterOneBookFails() throws Exception {
        User user = User.builder().id(1L).build();
        CrawlerExportService exports = mock(CrawlerExportService.class);
        List<String> formats = List.of("STRUCTURED", "EPUB");
        when(exports.importLibrary(user, 1L, formats)).thenReturn(101L);
        when(exports.importLibrary(user, 2L, formats)).thenThrow(
                new ResponseStatusException(HttpStatus.CONFLICT, "没有可用正文"));
        when(exports.importLibrary(user, 3L, formats)).thenReturn(103L);
        CrawlerBatchImportService service = new CrawlerBatchImportService(exports);
        try {
            JobView job = service.submit(user, List.of(1L, 2L, 1L, 3L), formats);
            long deadline = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos();
            while (!job.status().equals("COMPLETED") && System.nanoTime() < deadline) {
                Thread.sleep(10);
                job = service.progress(user, job.id());
            }
            assertThat(job.status()).isEqualTo("COMPLETED");
            assertThat(job.total()).isEqualTo(3);
            assertThat(job.completed()).isEqualTo(3);
            assertThat(job.results()).extracting(item -> item.crawlerBookId())
                    .containsExactly(1L, 2L, 3L);
            assertThat(job.results()).extracting(item -> item.success())
                    .containsExactly(true, false, true);
            assertThat(job.results().get(1).reason()).isEqualTo("没有可用正文");
            verify(exports).importLibrary(user, 1L, formats);
            verify(exports).importLibrary(user, 2L, formats);
            verify(exports).importLibrary(user, 3L, formats);
            verifyNoMoreInteractions(exports);
            String jobId = job.id();
            assertThatThrownBy(() -> service.progress(User.builder().id(2L).build(), jobId))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        } finally {
            service.close();
        }
    }
}
