package com.aibook.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "crawler_chapter_attempt_metrics", indexes = {
        @Index(name = "idx_crawler_attempt_user_started", columnList = "user_id,attempt_started_at"),
        @Index(name = "idx_crawler_attempt_started", columnList = "attempt_started_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlerChapterAttemptMetric {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "task_id", length = 36)
    private String taskId;

    @Column(name = "site_name", nullable = false, length = 200)
    private String siteName;

    @Column(name = "site_theme_color", length = 7)
    private String siteThemeColor;

    @Column(name = "book_name", nullable = false, length = 500)
    private String bookName;

    @Column(name = "chapter_name", nullable = false, length = 500)
    private String chapterName;

    @Column(name = "chapter_index")
    private Integer chapterIndex;

    @Column(name = "attempt_started_at", nullable = false)
    private LocalDateTime attemptStartedAt;

    @Column(name = "attempt_finished_at", nullable = false)
    private LocalDateTime attemptFinishedAt;

    @Column(name = "collection_millis", nullable = false)
    private Long collectionMillis;

    @Column(name = "fixed_wait_millis", nullable = false)
    private Long fixedWaitMillis;

    @Column(name = "random_wait_millis", nullable = false)
    private Long randomWaitMillis;

    @Column(name = "other_wait_millis", nullable = false)
    private Long otherWaitMillis;

    @Column(name = "total_elapsed_millis", nullable = false)
    private Long totalElapsedMillis;

    @Column(nullable = false, length = 32)
    private String outcome;
}
