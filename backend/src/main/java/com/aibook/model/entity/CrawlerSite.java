package com.aibook.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "crawler_sites", uniqueConstraints =
        @UniqueConstraint(name = "uk_crawler_site_user_code", columnNames = {"user_id", "site_code"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlerSite {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false, length = 100)
    private String siteName;
    @Column(name = "site_code", nullable = false, length = 80)
    private String siteCode;
    @Column(nullable = false, length = 1000)
    private String baseUrl;
    @Column(length = 1000)
    private String homeUrl;
    @Builder.Default private Boolean enabled = false;
    @Builder.Default private Boolean autoScan = false;
    @Builder.Default private Boolean autoCrawl = false;
    @Builder.Default private Boolean autoUpdate = true;
    @Builder.Default private Boolean autoImportLibrary = false;
    @Builder.Default private Integer scanIntervalMinutes = 360;
    @Builder.Default private Integer updateIntervalMinutes = 30;
    @Builder.Default private Integer requestIntervalMillis = 1500;
    @Builder.Default private Integer randomDelayMillis = 1000;
    @Builder.Default private Integer maxConcurrency = 1;
    @Builder.Default private Integer timeoutMillis = 15000;
    @Builder.Default private Integer retryCount = 2;
    @Builder.Default private String encoding = "UTF-8";
    @Column(length = 500) private String userAgent;
    @Column(columnDefinition = "TEXT") private String cookie;
    @Column(columnDefinition = "TEXT") private String headersJson;
    @Column(length = 1000) private String proxy;
    @Enumerated(EnumType.STRING) @Builder.Default private ParserType parserType = ParserType.CONFIG;
    private String parserBean;
    @Enumerated(EnumType.STRING) @Builder.Default private SiteStatus status = SiteStatus.READY;
    @OneToOne(mappedBy = "site", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private CrawlerSiteRule rule;
    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;

    public void attachRule(CrawlerSiteRule value) {
        rule = value;
        if (value != null) value.setSite(this);
    }

    public enum ParserType { CONFIG, CUSTOM }
    public enum SiteStatus { READY, PAUSED, RULE_ERROR }
}
