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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 正式书库中的结构化章节快照。
 *
 * <p>快照归属于不可变的书籍版本，不直接引用可被重新采集覆盖的 CrawlerChapter。</p>
 */
@Entity
@Table(name = "library_chapters",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_library_chapter_version_index",
                        columnNames = {"book_version_id", "chapter_index"}),
                @UniqueConstraint(name = "uk_library_chapter_version_key",
                        columnNames = {"book_version_id", "chapter_key"})
        },
        indexes = @Index(name = "idx_library_chapter_version_index",
                columnList = "book_version_id,chapter_index"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LibraryChapter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_version_id", nullable = false)
    private BookVersion bookVersion;

    /** 源站章节稳定标识；源站未提供时由章节 URL 生成。 */
    @Column(name = "chapter_key", nullable = false, length = 500)
    private String chapterKey;

    @Column(name = "chapter_index", nullable = false)
    private Integer chapterIndex;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(nullable = false, length = 64)
    private String contentHash;

    @Builder.Default
    private Integer wordCount = 0;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
