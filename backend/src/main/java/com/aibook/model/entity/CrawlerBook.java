package com.aibook.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "crawler_books", uniqueConstraints =
        @UniqueConstraint(name = "uk_crawler_book_site_external", columnNames = {"site_id", "external_book_id"}),
        indexes = @Index(name = "idx_crawler_book_site_status", columnList = "site_id,crawl_status"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CrawlerBook {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "site_id", nullable = false) private CrawlerSite site;
    @Column(name = "external_book_id", nullable = false, length = 300) private String externalBookId;
    @Column(nullable = false, length = 1500) private String bookUrl;
    @Column(nullable = false) private String bookName;
    private String author;
    @Column(length = 1500) private String coverUrl;
    private String coverLocalPath;
    @Column(columnDefinition = "TEXT") private String description;
    private String category;
    @Column(columnDefinition = "TEXT") private String tags;
    private String bookStatus;
    private String latestChapter;
    @Column(length = 1500) private String latestChapterUrl;
    private LocalDateTime sourceUpdateTime;
    private LocalDateTime discoverTime;
    private LocalDateTime lastCrawlTime;
    private LocalDateTime lastUpdateCheckTime;
    @Builder.Default private Integer chapterCount = 0;
    @Builder.Default private Integer crawledChapterCount = 0;
    @Builder.Default private Integer failedChapterCount = 0;
    @Enumerated(EnumType.STRING) @Builder.Default private CrawlStatus crawlStatus = CrawlStatus.DISCOVERED;
    @Enumerated(EnumType.STRING) @Builder.Default private ImportStatus importStatus = ImportStatus.NOT_IMPORTED;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "library_book_id") private Book libraryBook;
    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;

    public enum CrawlStatus { DISCOVERED, WAITING, CRAWLING_METADATA, CRAWLING_CHAPTER_LIST, CRAWLING_CONTENT, PARTIAL_SUCCESS, COMPLETED, FAILED, UPDATING, PAUSED }
    public enum ImportStatus { NOT_IMPORTED, READY, IMPORTED }
}
