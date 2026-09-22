package com.aibook.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "crawler_books", uniqueConstraints =
        @UniqueConstraint(name = "uk_crawler_book_site_external", columnNames = {"site_id", "external_book_id"}),
        indexes = {
                @Index(name = "idx_crawler_book_site_status", columnList = "site_id,crawl_status"),
                @Index(name = "idx_crawler_book_site_favorite", columnList = "site_id,favorite")
        })
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
    private Long discoveryPageId;
    @Column(length = 100) private String discoveryPageName;
    private LocalDateTime lastCrawlStartedAt;
    private LocalDateTime lastCrawlTime;
    private LocalDateTime lastUpdateCheckTime;
    @Builder.Default private Integer chapterCount = 0;
    @Builder.Default private Integer crawledChapterCount = 0;
    @Builder.Default private Integer pendingReleaseChapterCount = 0;
    @Builder.Default private Integer failedChapterCount = 0;
    @Column(nullable = false, columnDefinition = "boolean default true")
    @Builder.Default private Boolean autoUpdateEnabled = true;
    @Column(nullable = false, columnDefinition = "boolean default true")
    @Builder.Default private Boolean autoSyncLibrary = true;
    // 收藏由独立更新语句维护，避免长时间运行的采集任务用旧实体快照覆盖用户选择。
    @Column(nullable = false, updatable = false, columnDefinition = "boolean default false")
    @Builder.Default private Boolean favorite = false;
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "crawler_book_lists",
            joinColumns = @JoinColumn(name = "crawler_book_id"),
            inverseJoinColumns = @JoinColumn(name = "book_list_id"))
    @Builder.Default private List<BookList> bookLists = new ArrayList<>();
    @Enumerated(EnumType.STRING) @Builder.Default private CrawlStatus crawlStatus = CrawlStatus.DISCOVERED;
    @Enumerated(EnumType.STRING) @Builder.Default private DiscoveryStatus discoveryStatus = DiscoveryStatus.ACTIVE;
    @Enumerated(EnumType.STRING) @Builder.Default private ImportStatus importStatus = ImportStatus.NOT_IMPORTED;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "library_book_id") private Book libraryBook;
    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;

    @PostLoad
    @PrePersist
    @PreUpdate
    private void normalizeDefaults() {
        if (discoveryStatus == null) discoveryStatus = DiscoveryStatus.ACTIVE;
        if (autoUpdateEnabled == null) autoUpdateEnabled = true;
        if (autoSyncLibrary == null) autoSyncLibrary = true;
        if (favorite == null) favorite = false;
        if (pendingReleaseChapterCount == null) pendingReleaseChapterCount = 0;
    }

    public enum CrawlStatus { DISCOVERED, WAITING, CRAWLING_METADATA, CRAWLING_CHAPTER_LIST, CRAWLING_CONTENT, PARTIAL_SUCCESS, COMPLETED, FAILED, UPDATING, PAUSED }
    public enum DiscoveryStatus { ACTIVE, IGNORED, BLACKLISTED }
    public enum ImportStatus { NOT_IMPORTED, READY, IMPORTED }
}
