package com.aibook.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** Persistent crawler execution log, including chapter-level progress details. */
@Entity
@Table(
        name = "crawler_task_logs",
        indexes = @Index(
                name = "idx_crawler_task_logs_user_book_created",
                columnList = "user_id,crawler_book_id,created_at"))
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlerTaskLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "crawler_book_id")
    private Long crawlerBookId;

    @Column(nullable = false, length = 36)
    private String taskId;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(columnDefinition = "TEXT")
    private String details;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
