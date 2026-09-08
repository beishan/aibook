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
     * 当前阅读定位。EPUB 保存 CFI，文本书籍保存章节名称。
     */
    private String currentChapter;

    /**
     * 当前章节的可读标题。
     */
    private String currentChapterTitle;

    /** 当前主聚合进度对应的跨格式阅读定位 JSON。 */
    @Column(columnDefinition = "TEXT")
    private String locator;

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
