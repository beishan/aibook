package com.aibook.service.crawler;

import com.aibook.dto.crawler.CrawlerDtos.ChapterAttemptDailyView;
import com.aibook.dto.crawler.CrawlerDtos.ChapterAttemptStatisticsView;
import com.aibook.dto.crawler.CrawlerDtos.ChapterAttemptView;
import com.aibook.model.entity.CrawlerChapterAttemptMetric;
import com.aibook.model.entity.CrawlerChapter;
import com.aibook.model.entity.CrawlerTask;
import com.aibook.model.entity.User;
import com.aibook.repository.CrawlerChapterAttemptMetricRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrawlerChapterAttemptMetricService {
    private static final int RETENTION_DAYS = 180;

    private final CrawlerChapterAttemptMetricRepository metricRepository;

    @Transactional
    public void record(User user, CrawlerTask task, CrawlerChapter chapter,
            LocalDateTime startedAt, LocalDateTime finishedAt,
            long collectionMillis, long fixedWaitMillis, long randomWaitMillis,
            long otherWaitMillis, long totalElapsedMillis, String outcome) {
        CrawlerChapterAttemptMetric metric = CrawlerChapterAttemptMetric.builder()
                .userId(user.getId())
                .taskId(task.getId())
                .siteName(task.getSite().getSiteName())
                .siteThemeColor(task.getSite().getThemeColor())
                .bookName(chapter.getCrawlerBook().getBookName())
                .chapterName(chapter.getChapterName())
                .chapterIndex(chapter.getChapterIndex())
                .attemptStartedAt(startedAt)
                .attemptFinishedAt(finishedAt)
                .collectionMillis(collectionMillis)
                .fixedWaitMillis(fixedWaitMillis)
                .randomWaitMillis(randomWaitMillis)
                .otherWaitMillis(otherWaitMillis)
                .totalElapsedMillis(totalElapsedMillis)
                .outcome(outcome)
                .build();
        metricRepository.save(metric);
    }

    @Transactional(readOnly = true)
    public ChapterAttemptStatisticsView statistics(User user, int requestedDays,
            int requestedPage, int requestedSize) {
        int days = Math.max(1, Math.min(requestedDays, RETENTION_DAYS));
        int page = Math.max(0, requestedPage);
        int size = Math.max(1, Math.min(requestedSize, 100));
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(days);
        Map<LocalDate, ChapterAttemptDailyView> valuesByDay = new HashMap<>();

        for (Object[] row : metricRepository.averageDurationsByDay(user.getId(), start, end)) {
            LocalDate date = toDate(row[0]);
            valuesByDay.put(date, new ChapterAttemptDailyView(
                    date,
                    number(row[1]).longValue(),
                    roundedAverage(row[2]),
                    roundedAverage(row[3]),
                    roundedAverage(row[4]),
                    roundedAverage(row[5]),
                    roundedAverage(row[6])));
        }

        List<ChapterAttemptDailyView> daily = new ArrayList<>(days);
        LocalDate firstDay = start.toLocalDate();
        LocalDate lastDay = end.toLocalDate();
        for (LocalDate date = firstDay; !date.isAfter(lastDay); date = date.plusDays(1)) {
            daily.add(valuesByDay.getOrDefault(date,
                    new ChapterAttemptDailyView(date, 0, 0, 0, 0, 0, 0)));
        }

        var attempts = metricRepository.findByUserIdAndAttemptStartedAtBetween(
                user.getId(), start, end,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "attemptStartedAt")))
                .map(this::toView);
        return new ChapterAttemptStatisticsView(days, daily, attempts);
    }

    @Scheduled(cron = "0 17 3 * * *")
    @Transactional
    public void removeExpiredMetrics() {
        long deleted = metricRepository.deleteByAttemptStartedAtBefore(
                LocalDateTime.now().minusDays(RETENTION_DAYS));
        if (deleted > 0) {
            log.info("[采集统计] 已清理超过 {} 天的章节耗时记录: {}", RETENTION_DAYS, deleted);
        }
    }

    private ChapterAttemptView toView(CrawlerChapterAttemptMetric metric) {
        return new ChapterAttemptView(
                metric.getId(), metric.getTaskId(), metric.getSiteName(),
                metric.getSiteThemeColor(), metric.getBookName(), metric.getChapterName(),
                metric.getChapterIndex(),
                metric.getAttemptStartedAt(), metric.getAttemptFinishedAt(),
                metric.getCollectionMillis(), metric.getFixedWaitMillis(),
                metric.getRandomWaitMillis(), metric.getOtherWaitMillis(),
                metric.getTotalElapsedMillis(), metric.getOutcome());
    }

    private LocalDate toDate(Object value) {
        if (value instanceof LocalDate date) return date;
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        return LocalDate.parse(value.toString());
    }

    private Number number(Object value) {
        return value instanceof Number number ? number : 0;
    }

    private long roundedAverage(Object value) {
        return Math.round(number(value).doubleValue());
    }
}
