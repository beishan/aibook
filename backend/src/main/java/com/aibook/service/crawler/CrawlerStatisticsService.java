package com.aibook.service.crawler;

import com.aibook.dto.crawler.CrawlerDtos.ChapterAttemptView;
import com.aibook.dto.crawler.CrawlerDtos.CrawlerFunnelView;
import com.aibook.dto.crawler.CrawlerDtos.SiteContributionView;
import com.aibook.dto.crawler.CrawlerDtos.TimeBucketAttemptStatisticsView;
import com.aibook.dto.crawler.CrawlerDtos.TimeBucketAttemptView;
import com.aibook.dto.crawler.CrawlerDtos.TimeBucketDashboardView;
import com.aibook.dto.crawler.CrawlerDtos.TimeBucketStatisticsView;
import com.aibook.model.entity.CrawlerBook;
import com.aibook.model.entity.CrawlerChapterAttemptMetric;
import com.aibook.model.entity.User;
import com.aibook.repository.CrawlerBookRepository;
import com.aibook.repository.CrawlerChapterAttemptMetricRepository;
import com.aibook.repository.CrawlerTaskRepository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Aggregates in PostgreSQL, keeping raw chapter rows out of application memory. */
@Service
@RequiredArgsConstructor
public class CrawlerStatisticsService {
    private final NamedParameterJdbcTemplate jdbc;
    private final CrawlerBookRepository bookRepository;
    private final CrawlerTaskRepository taskRepository;
    private final CrawlerChapterAttemptMetricRepository metricRepository;

    record Window(String granularity, int range, ChronoUnit unit,
            LocalDateTime start, LocalDateTime end) {
        static Window create(String granularity, int range, LocalDateTime end) {
            ChronoUnit unit;
            Set<Integer> allowed;
            switch (granularity) {
                case "day" -> {
                    unit = ChronoUnit.DAYS;
                    allowed = Set.of(7, 30, 90);
                }
                case "hour" -> {
                    unit = ChronoUnit.HOURS;
                    allowed = Set.of(24, 48, 72);
                }
                case "minute" -> {
                    unit = ChronoUnit.MINUTES;
                    allowed = Set.of(30, 60, 180);
                }
                default -> throw invalidWindow();
            }
            if (!allowed.contains(range)) throw invalidWindow();
            return new Window(granularity, range, unit,
                    end.truncatedTo(unit).minus(range - 1L, unit), end);
        }

        private static ResponseStatusException invalidWindow() {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "统计范围支持：7/30/90 天、24/48/72 小时、30/60/180 分钟");
        }

        String label(LocalDateTime time) {
            return unit == ChronoUnit.DAYS ? time.toLocalDate().toString() : time.toString();
        }

        Map<LocalDateTime, long[]> emptyBuckets(int columns) {
            Map<LocalDateTime, long[]> buckets = new LinkedHashMap<>();
            for (int i = 0; i < range; i++) {
                buckets.put(start.plus(i, unit), new long[columns]);
            }
            return buckets;
        }

        MapSqlParameterSource parameters(Long userId) {
            return new MapSqlParameterSource("userId", userId)
                    .addValue("granularity", granularity)
                    .addValue("start", Timestamp.valueOf(start))
                    .addValue("end", Timestamp.valueOf(end));
        }
    }

    @Transactional(readOnly = true)
    public TimeBucketDashboardView dashboard(User user, String granularity, int range) {
        Window window = Window.create(granularity, range, LocalDateTime.now());
        var parameters = window.parameters(user.getId());
        var buckets = window.emptyBuckets(7);
        mergeCounts(buckets, parameters, """
                crawler_chapters c
                join crawler_books b on b.id = c.crawler_book_id
                join crawler_sites s on s.id = b.site_id
                """, "s.user_id", "c.created_at", "", 0);
        mergeCounts(buckets, parameters, """
                crawler_chapters c
                join crawler_books b on b.id = c.crawler_book_id
                join crawler_sites s on s.id = b.site_id
                """, "s.user_id", "c.crawl_time", "and c.crawl_status = 'COMPLETED'", 1);
        mergeCounts(buckets, parameters,
                "crawler_books b join crawler_sites s on s.id = b.site_id",
                "s.user_id", "b.created_at", "", 2);
        mergeCounts(buckets, parameters,
                "crawler_books b join crawler_sites s on s.id = b.site_id",
                "s.user_id", "b.last_crawl_time", "and b.crawl_status = 'COMPLETED'", 5);
        jdbc.query("""
                select date_trunc(:granularity, t.finished_at) as bucket,
                    count(*) as total,
                    count(*) filter (where t.status = 'SUCCESS') as success,
                    count(*) filter (where t.status = 'FAILED') as failed
                from crawler_tasks t
                where t.user_id = :userId and t.finished_at >= :start and t.finished_at < :end
                  and t.status in ('SUCCESS', 'PARTIAL_SUCCESS', 'FAILED')
                group by bucket order by bucket
                """, parameters, rs -> {
                    long[] values = buckets.get(rs.getTimestamp("bucket").toLocalDateTime());
                    if (values != null) {
                        values[3] = rs.getLong("total");
                        values[4] = rs.getLong("success");
                        values[6] = rs.getLong("failed");
                    }
                });
        List<TimeBucketStatisticsView> points = buckets.entrySet().stream()
                .map(entry -> {
                    long[] v = entry.getValue();
                    return new TimeBucketStatisticsView(window.label(entry.getKey()),
                            v[0], v[1], v[2], v[3], v[4], v[5], v[6]);
                }).toList();
        List<SiteContributionView> contributions = jdbc.query("""
                select s.id, s.site_name, sum(events.books) as books,
                    sum(events.chapters) as chapters
                from (
                    select b.site_id, count(*) as books, 0 as chapters
                    from crawler_books b join crawler_sites s on s.id = b.site_id
                    where s.user_id = :userId and b.created_at >= :start and b.created_at < :end
                    group by b.site_id
                    union all
                    select b.site_id, 0 as books, count(*) as chapters
                    from crawler_chapters c join crawler_books b on b.id = c.crawler_book_id
                    join crawler_sites s on s.id = b.site_id
                    where s.user_id = :userId and c.created_at >= :start and c.created_at < :end
                    group by b.site_id
                ) events join crawler_sites s on s.id = events.site_id
                group by s.id, s.site_name
                order by chapters desc, books desc, s.site_name, s.id limit 5
                """, parameters, (rs, row) -> new SiteContributionView(rs.getLong("id"),
                        rs.getString("site_name"), rs.getLong("books"), rs.getLong("chapters")));
        var funnel = new CrawlerFunnelView(bookRepository.countBySiteUser(user),
                taskRepository.countDistinctTaskedBooks(user),
                bookRepository.countBySiteUserAndCrawlStatus(user, CrawlerBook.CrawlStatus.COMPLETED),
                bookRepository.countBySiteUserAndImportStatus(user, CrawlerBook.ImportStatus.IMPORTED));
        return new TimeBucketDashboardView(granularity, range, granularity.equals("day") ? range : 0, window.start(), window.end(),
                points, contributions, funnel);
    }

    private void mergeCounts(Map<LocalDateTime, long[]> buckets,
            MapSqlParameterSource parameters, String source, String owner, String timestamp,
            String condition, int index) {
        // All identifiers come from the fixed calls above; user input is bound as parameters.
        String sql = "select date_trunc(:granularity, " + timestamp + ") as bucket, count(*) as total"
                + " from " + source + " where " + owner + " = :userId and " + timestamp
                + " >= :start and " + timestamp + " < :end " + condition
                + " group by bucket order by bucket";
        jdbc.query(sql, parameters, rs -> {
            long[] values = buckets.get(rs.getTimestamp("bucket").toLocalDateTime());
            if (values != null) values[index] = rs.getLong("total");
        });
    }

    @Transactional(readOnly = true)
    public TimeBucketAttemptStatisticsView attempts(User user, String granularity, int range,
            LocalDateTime requestedEnd, int page, int size) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime end = requestedEnd == null || requestedEnd.isAfter(now) ? now : requestedEnd;
        Window window = Window.create(granularity, range, end);
        var buckets = window.emptyBuckets(6);
        jdbc.query("""
                select date_trunc(:granularity, attempt_started_at) as bucket,
                    count(*) as attempts, avg(collection_millis) as collection,
                    avg(fixed_wait_millis) as fixed, avg(random_wait_millis) as random,
                    avg(other_wait_millis) as other, avg(total_elapsed_millis) as total
                from crawler_chapter_attempt_metrics
                where user_id = :userId and attempt_started_at >= :start and attempt_started_at < :end
                group by bucket order by bucket
                """, window.parameters(user.getId()), rs -> {
                    long[] values = buckets.get(rs.getTimestamp("bucket").toLocalDateTime());
                    if (values != null) {
                        values[0] = rs.getLong("attempts");
                        values[1] = Math.round(rs.getDouble("collection"));
                        values[2] = Math.round(rs.getDouble("fixed"));
                        values[3] = Math.round(rs.getDouble("random"));
                        values[4] = Math.round(rs.getDouble("other"));
                        values[5] = Math.round(rs.getDouble("total"));
                    }
                });
        var points = buckets.entrySet().stream().map(entry -> {
            long[] v = entry.getValue();
            return new TimeBucketAttemptView(window.label(entry.getKey()),
                    v[0], v[1], v[2], v[3], v[4], v[5]);
        }).toList();
        var attempts = metricRepository.findInWindow(user.getId(), window.start(), window.end(),
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)),
                        Sort.by(Sort.Direction.DESC, "attemptStartedAt", "id"))).map(this::attemptView);
        return new TimeBucketAttemptStatisticsView(granularity, range, granularity.equals("day") ? range : 0, window.start(), window.end(),
                points, attempts);
    }

    private ChapterAttemptView attemptView(CrawlerChapterAttemptMetric metric) {
        return new ChapterAttemptView(metric.getId(), metric.getTaskId(), metric.getSiteName(),
                metric.getSiteThemeColor(), metric.getBookName(), metric.getChapterName(),
                metric.getChapterIndex(), metric.getAttemptStartedAt(), metric.getAttemptFinishedAt(),
                metric.getCollectionMillis(), metric.getFixedWaitMillis(), metric.getRandomWaitMillis(),
                metric.getOtherWaitMillis(), metric.getTotalElapsedMillis(), metric.getOutcome());
    }
}
