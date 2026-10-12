package com.aibook.service.crawler;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aibook.model.entity.CrawlerChapterAttemptMetric;
import com.aibook.model.entity.User;
import com.aibook.repository.CrawlerBookRepository;
import com.aibook.repository.CrawlerChapterAttemptMetricRepository;
import com.aibook.repository.CrawlerTaskRepository;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class CrawlerStatisticsServiceTest {
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final CrawlerBookRepository books = mock(CrawlerBookRepository.class);
    private final CrawlerTaskRepository tasks = mock(CrawlerTaskRepository.class);
    private final CrawlerChapterAttemptMetricRepository metrics =
            mock(CrawlerChapterAttemptMetricRepository.class);
    private final CrawlerStatisticsService service =
            new CrawlerStatisticsService(jdbc, books, tasks, metrics);
    private final User user = User.builder().id(7L).build();

    @ParameterizedTest
    @CsvSource({"day,7", "day,30", "day,90", "hour,24", "hour,48", "hour,72",
            "minute,30", "minute,60", "minute,180"})
    void suppliesExactlyTheRequestedNumberOfOrderedZeroBuckets(String unit, int range) {
        var result = service.dashboard(user, unit, range);
        assertThat(result.daily()).hasSize(range);
        assertThat(result.daily()).extracting(point -> point.date()).isSorted();
        assertThat(result.daily()).allSatisfy(point -> {
            assertThat(point.newChapters()).isZero();
            assertThat(point.finishedTasks()).isZero();
            assertThat(point.failedTasks()).isZero();
        });
        assertThat(result.granularity()).isEqualTo(unit);
        assertThat(result.days()).isEqualTo(unit.equals("day") ? range : 0);
        assertThat(result.start()).isBefore(result.end());
    }

    @ParameterizedTest
    @CsvSource({"day,24", "hour,30", "minute,90", "second,60", "minute,0", "day,2147483647"})
    void rejectsInvalidAndUnboundedWindowsBeforeQuerying(String unit, int range) {
        assertThatThrownBy(() -> service.dashboard(user, unit, range))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(jdbc, books, tasks, metrics);
    }

    @Test
    void windowsCrossMidnightWithoutDroppingTheCurrentMinute() {
        var end = LocalDateTime.of(2026, 10, 1, 0, 1, 30);
        var window = CrawlerStatisticsService.Window.create("minute", 30, end);
        assertThat(window.start()).isEqualTo(LocalDateTime.of(2026, 9, 30, 23, 32));
        assertThat(window.emptyBuckets(1)).hasSize(30)
                .containsKey(LocalDateTime.of(2026, 10, 1, 0, 1));
        assertThat(window.parameters(7L).getValue("end")).isEqualTo(Timestamp.valueOf(end));
    }

    @Test
    void aggregatesAccountScopedCountsAndIncludesPartialTasksOnlyInTheTotal() throws Exception {
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            MapSqlParameterSource params = invocation.getArgument(1);
            assertThat(params.getValue("userId")).isEqualTo(7L);
            assertThat(params.getValue("granularity")).isEqualTo("hour");
            assertThat(sql).contains("< :end", ">= :start", "= :userId");
            ResultSet row = mock(ResultSet.class);
            when(row.getTimestamp("bucket")).thenReturn((Timestamp) params.getValue("start"));
            if (sql.contains("from crawler_tasks")) {
                assertThat(sql).contains("'SUCCESS', 'PARTIAL_SUCCESS', 'FAILED'")
                        .doesNotContain("CANCELLED");
                when(row.getLong("total")).thenReturn(6L);
                when(row.getLong("success")).thenReturn(3L);
                when(row.getLong("failed")).thenReturn(1L);
            } else {
                when(row.getLong("total")).thenReturn(4L);
            }
            invocation.<RowCallbackHandler>getArgument(2).processRow(row);
            return null;
        }).when(jdbc).query(anyString(), any(MapSqlParameterSource.class), any(RowCallbackHandler.class));
        var result = service.dashboard(user, "hour", 24);
        var first = result.daily().getFirst();
        assertThat(first.newChapters()).isEqualTo(4);
        assertThat(first.successfulChapters()).isEqualTo(4);
        assertThat(first.newBooks()).isEqualTo(4);
        assertThat(first.successfulBooks()).isEqualTo(4);
        assertThat(first.finishedTasks()).isEqualTo(6);
        assertThat(first.successfulTasks()).isEqualTo(3);
        assertThat(first.failedTasks()).isEqualTo(1);
        assertThat(result.daily().get(1).newChapters()).isZero();
    }

    @Test
    void averagesAndDetailPaginationShareTheExactHalfOpenWindow() throws Exception {
        var end = LocalDateTime.of(2026, 10, 1, 0, 1, 30);
        var start = end.withSecond(0).minusMinutes(59);
        var metric = CrawlerChapterAttemptMetric.builder().id(42L).userId(7L)
                .attemptStartedAt(start).collectionMillis(1501L).fixedWaitMillis(0L)
                .randomWaitMillis(0L).otherWaitMillis(0L).totalElapsedMillis(1501L)
                .outcome("SUCCESS").build();
        when(metrics.findInWindow(eq(7L), eq(start), eq(end), any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(metric)));
        doAnswer(invocation -> {
            MapSqlParameterSource params = invocation.getArgument(1);
            assertThat(params.getValue("userId")).isEqualTo(7L);
            assertThat(params.getValue("start")).isEqualTo(Timestamp.valueOf(start));
            assertThat(params.getValue("end")).isEqualTo(Timestamp.valueOf(end));
            ResultSet row = mock(ResultSet.class);
            when(row.getTimestamp("bucket")).thenReturn(Timestamp.valueOf(start));
            when(row.getLong("attempts")).thenReturn(2L);
            when(row.getDouble("collection")).thenReturn(1500.6);
            when(row.getDouble("total")).thenReturn(2000.2);
            invocation.<RowCallbackHandler>getArgument(2).processRow(row);
            return null;
        }).when(jdbc).query(anyString(), any(MapSqlParameterSource.class), any(RowCallbackHandler.class));
        var result = service.attempts(user, "minute", 60, end, 2, 20);
        assertThat(result.daily()).hasSize(60);
        assertThat(result.daily().getFirst().attempts()).isEqualTo(2);
        assertThat(result.daily().getFirst().collectionMillis()).isEqualTo(1501);
        assertThat(result.daily().getFirst().totalElapsedMillis()).isEqualTo(2000);
        assertThat(result.daily().get(1).attempts()).isZero();
        assertThat(result.attempts().getContent().getFirst().id()).isEqualTo(42L);
        verify(metrics).findInWindow(eq(7L), eq(start), eq(end), argThat(page ->
                page.getPageNumber() == 2 && page.getPageSize() == 20));
    }
}
