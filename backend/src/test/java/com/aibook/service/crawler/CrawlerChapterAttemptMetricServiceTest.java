package com.aibook.service.crawler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aibook.model.entity.CrawlerBook;
import com.aibook.model.entity.CrawlerChapter;
import com.aibook.model.entity.CrawlerChapterAttemptMetric;
import com.aibook.model.entity.CrawlerSite;
import com.aibook.model.entity.CrawlerTask;
import com.aibook.model.entity.User;
import com.aibook.repository.CrawlerChapterAttemptMetricRepository;
import java.time.LocalDateTime;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class CrawlerChapterAttemptMetricServiceTest {

    @ParameterizedTest
    @ValueSource(strings = {"SUCCESS", "FAILED", "INTERRUPTED"})
    void recordsTimingWithoutInitializingDetachedChapterBook(String outcome) {
        CrawlerChapterAttemptMetricRepository repository =
                mock(CrawlerChapterAttemptMetricRepository.class);
        CrawlerChapterAttemptMetricService service =
                new CrawlerChapterAttemptMetricService(repository);
        // Reproduce the inaccessible book association supplied by the background worker.
        CrawlerBook detachedBook = mock(CrawlerBook.class);
        when(detachedBook.getBookName()).thenThrow(
                new LazyInitializationException("could not initialize proxy - no Session"));
        CrawlerChapter chapter = CrawlerChapter.builder()
                .id(707901L)
                .crawlerBook(detachedBook)
                .chapterName("第十章")
                .chapterIndex(10)
                .build();
        User user = User.builder().id(7L).build();
        CrawlerSite site = CrawlerSite.builder()
                .siteName("示例站点")
                .themeColor("#123456")
                .build();
        CrawlerTask task = CrawlerTask.builder().id("attempt-task").site(site).build();
        LocalDateTime startedAt = LocalDateTime.of(2026, 10, 6, 10, 0);
        LocalDateTime finishedAt = startedAt.plusSeconds(2);

        service.record(user, task, chapter, "已加载的书名", startedAt, finishedAt,
                1000, 500, 300, 200, 2000, outcome);

        ArgumentCaptor<CrawlerChapterAttemptMetric> saved =
                ArgumentCaptor.forClass(CrawlerChapterAttemptMetric.class);
        verify(repository).save(saved.capture());
        verifyNoInteractions(detachedBook);
        CrawlerChapterAttemptMetric metric = saved.getValue();
        assertThat(metric.getUserId()).isEqualTo(7L);
        assertThat(metric.getTaskId()).isEqualTo("attempt-task");
        assertThat(metric.getSiteName()).isEqualTo("示例站点");
        assertThat(metric.getSiteThemeColor()).isEqualTo("#123456");
        assertThat(metric.getBookName()).isEqualTo("已加载的书名");
        assertThat(metric.getChapterName()).isEqualTo("第十章");
        assertThat(metric.getChapterIndex()).isEqualTo(10);
        assertThat(metric.getAttemptStartedAt()).isEqualTo(startedAt);
        assertThat(metric.getAttemptFinishedAt()).isEqualTo(finishedAt);
        assertThat(metric.getCollectionMillis()).isEqualTo(1000L);
        assertThat(metric.getFixedWaitMillis()).isEqualTo(500L);
        assertThat(metric.getRandomWaitMillis()).isEqualTo(300L);
        assertThat(metric.getOtherWaitMillis()).isEqualTo(200L);
        assertThat(metric.getTotalElapsedMillis()).isEqualTo(2000L);
        assertThat(metric.getOutcome()).isEqualTo(outcome);
    }
}
