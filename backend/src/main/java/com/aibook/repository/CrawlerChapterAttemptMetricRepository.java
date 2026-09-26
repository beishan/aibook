package com.aibook.repository;

import com.aibook.model.entity.CrawlerChapterAttemptMetric;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CrawlerChapterAttemptMetricRepository
        extends JpaRepository<CrawlerChapterAttemptMetric, Long> {

    Page<CrawlerChapterAttemptMetric> findByUserIdAndAttemptStartedAtBetween(
            Long userId,
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable);

    @Query("""
            select cast(metric.attemptStartedAt as LocalDate), count(metric),
                avg(metric.collectionMillis), avg(metric.fixedWaitMillis),
                avg(metric.randomWaitMillis), avg(metric.otherWaitMillis),
                avg(metric.totalElapsedMillis)
            from CrawlerChapterAttemptMetric metric
            where metric.userId = :userId
              and metric.attemptStartedAt >= :start
              and metric.attemptStartedAt < :end
            group by cast(metric.attemptStartedAt as LocalDate)
            order by cast(metric.attemptStartedAt as LocalDate)
            """)
    List<Object[]> averageDurationsByDay(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    long deleteByAttemptStartedAtBefore(LocalDateTime before);
}
