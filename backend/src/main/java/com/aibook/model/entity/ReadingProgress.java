package com.aibook.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 阅读进度实体
 */
@Entity
@Table(name = "reading_progress",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"user_id", "book_id"}
    ))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadingProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 书籍
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    /**
     * 用户
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 当前章节
     */
    private String currentChapter;

    /**
     * 章节内进度百分比 (0-100)
     */
    @Builder.Default
    private Integer chapterProgress = 0;

    /**
     * 总体进度百分比 (0-100)
     */
    @Builder.Default
    private Integer totalProgress = 0;

    /**
     * 阅读时长（秒）
     */
    @Builder.Default
    private Long readingTimeSeconds = 0L;

    /**
     * 最后阅读时间
     */
    private LocalDateTime lastReadAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
