package com.aibook.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "crawler_chapters", uniqueConstraints =
        @UniqueConstraint(name = "uk_crawler_chapter_book_external", columnNames = {"crawler_book_id", "external_chapter_id"}),
        indexes = @Index(name = "idx_crawler_chapter_book_index", columnList = "crawler_book_id,chapter_index"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CrawlerChapter {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crawler_book_id", nullable = false) private CrawlerBook crawlerBook;
    @Column(name = "external_chapter_id", nullable = false, length = 500) private String externalChapterId;
    @Column(name = "chapter_index", nullable = false) private Integer chapterIndex;
    @Column(nullable = false) private String chapterName;
    @Column(nullable = false, length = 1500) private String chapterUrl;
    @Column(columnDefinition = "TEXT") private String content;
    private String contentHash;
    @Builder.Default private Integer wordCount = 0;
    @Enumerated(EnumType.STRING) @Builder.Default private CrawlStatus crawlStatus = CrawlStatus.NOT_CRAWLED;
    @Enumerated(EnumType.STRING) @Builder.Default private AccessStatus accessStatus = AccessStatus.UNKNOWN;
    @Builder.Default private Integer retryCount = 0;
    private LocalDateTime sourceUpdateTime;
    private LocalDateTime crawlTime;
    @Column(columnDefinition = "TEXT") private String errorMessage;
    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;

    public enum CrawlStatus { NOT_CRAWLED, WAITING, CRAWLING, COMPLETED, FAILED, CONTENT_SUSPECTED, IGNORED }
    public enum AccessStatus { FREE, VIP, LOCKED, UNKNOWN }
}
