package com.aibook.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 按书籍文件版本隔离的阅读进度。
 */
@Entity
@Table(name = "version_reading_progress",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "version_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionReadingProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id", nullable = false)
    private BookVersion version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String currentChapter;

    private String currentChapterTitle;

    /** 跨格式阅读定位 JSON，保留 CFI、章节地址、文本段落及正文摘要。 */
    @Column(columnDefinition = "TEXT")
    private String locator;

    @Builder.Default
    private Integer chapterProgress = 0;

    @Builder.Default
    private Integer totalProgress = 0;

    @Builder.Default
    private Long readingTimeSeconds = 0L;

    /** 当前阅读会话及其已累计秒数，用于心跳请求幂等去重。 */
    private String readingSessionId;

    @Builder.Default
    private Long readingSessionElapsedSeconds = 0L;

    private LocalDateTime lastReadAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
