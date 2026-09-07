package com.aibook.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "crawler_tasks", indexes = @Index(name = "idx_crawler_task_user_status", columnList = "user_id,status"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CrawlerTask {
    @Id @Builder.Default private String id = UUID.randomUUID().toString();
    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "site_id", nullable = false) private CrawlerSite site;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "crawler_book_id") private CrawlerBook crawlerBook;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TaskType type;
    @Enumerated(EnumType.STRING) @Builder.Default private TaskStatus status = TaskStatus.WAITING;
    @Enumerated(EnumType.STRING) @Builder.Default private Priority priority = Priority.HIGH;
    @Builder.Default private Integer totalCount = 0;
    @Builder.Default private Integer successCount = 0;
    @Builder.Default private Integer failedCount = 0;
    @Builder.Default private Integer waitingCount = 0;
    private String currentChapter;
    private Long averageRequestMillis;
    @Column(columnDefinition = "TEXT") private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;

    public enum TaskType { SITE_SCAN, BOOK_METADATA, BOOK_CHAPTER_LIST, BOOK_CONTENT, BOOK_UPDATE_CHECK, BOOK_FULL_CRAWL, BOOK_EXPORT, BOOK_IMPORT }
    public enum TaskStatus { WAITING, RUNNING, PAUSED, SUCCESS, PARTIAL_SUCCESS, FAILED, CANCELLED }
    public enum Priority { LOW, NORMAL, HIGH }
}
