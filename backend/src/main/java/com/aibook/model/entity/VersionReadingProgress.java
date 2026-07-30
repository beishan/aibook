package com.aibook.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

    @Builder.Default
    private Integer chapterProgress = 0;

    @Builder.Default
    private Integer totalProgress = 0;

    @Builder.Default
    private Long readingTimeSeconds = 0L;

    private LocalDateTime lastReadAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
