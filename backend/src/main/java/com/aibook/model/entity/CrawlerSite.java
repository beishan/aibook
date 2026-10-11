package com.aibook.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "crawler_sites", uniqueConstraints =
        @UniqueConstraint(name = "uk_crawler_site_user_code", columnNames = {"user_id", "site_code"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlerSite {
    public static final String DEFAULT_THEME_COLOR = "#009688";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false, length = 100)
    private String siteName;
    @Column(name = "theme_color", length = 7)
    @Builder.Default
    private String themeColor = DEFAULT_THEME_COLOR;
    @Column(name = "site_code", nullable = false, length = 80)
    private String siteCode;
    @Column(nullable = false, length = 1000)
    private String baseUrl;
    @Column(length = 1000)
    private String homeUrl;
    @Builder.Default private Boolean enabled = false;
    @Builder.Default private Boolean manuallyFrozen = false;
    private Instant manualFreezeUntil;
    @Builder.Default private Integer manualFreezeDurationMinutes = 60;
    @Builder.Default private Boolean autoScan = false;
    @Builder.Default private Boolean autoCrawl = false;
    @Builder.Default private Boolean autoUpdate = true;
    @Builder.Default private Boolean autoImportLibrary = false;
    @Builder.Default private Integer scanIntervalMinutes = 360;
    @Builder.Default private Integer updateIntervalMinutes = 30;
    @Builder.Default private Integer maxDiscoveryPages = 3;
    @Builder.Default private String autoImportFormat = "EPUB";
    @Builder.Default private Integer requestIntervalMillis = 1500;
    @Builder.Default private Integer randomDelayMillis = 1000;
    @Builder.Default private Integer cooldownFailureThreshold = 5;
    private Integer maxRequestIntervalMillis;
    /** 独立随机等待范围；空值表示旧配置，仅使用原请求间隔范围。 */
    private Integer minRandomDelayMillis;
    private Integer maxRandomDelayMillis;

    public int fixedWaitMinimumMillis() {
        return Math.max(0, requestIntervalMillis == null ? 1500 : requestIntervalMillis);
    }

    public int fixedWaitMaximumMillis() {
        int minimum = fixedWaitMinimumMillis();
        return maxRequestIntervalMillis == null
                ? (int) Math.min(Integer.MAX_VALUE,
                        (long) minimum + Math.max(0, randomDelayMillis == null ? 1000 : randomDelayMillis))
                : Math.max(minimum, maxRequestIntervalMillis);
    }

    public int randomWaitMinimumMillis() {
        return Math.max(0, minRandomDelayMillis == null ? 0 : minRandomDelayMillis);
    }

    public int randomWaitMaximumMillis() {
        return Math.max(randomWaitMinimumMillis(), maxRandomDelayMillis == null ? 0 : maxRandomDelayMillis);
    }
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "crawler_site_blocked_windows",
            joinColumns = @JoinColumn(name = "site_id"))
    @OrderColumn(name = "window_order")
    @Builder.Default
    private List<CrawlerSiteAccessWindow> blockedAccessWindows = new ArrayList<>();
    @Builder.Default private Integer maxConcurrency = 1;
    @Builder.Default private Boolean respectRobotsTxt = true;
    @Builder.Default private String encoding = "UTF-8";
    /** 正文特征配置 JSON；兼容读取旧版本按行保存的失败特征。 */
    @Column(name = "content_failure_markers", columnDefinition = "TEXT") private String contentMarkersJson;
    @Column(length = 1000) private String proxy;
    @Column(columnDefinition = "TEXT") private String proxyConfigsJson;
    @Enumerated(EnumType.STRING) @Builder.Default private ParserType parserType = ParserType.CONFIG;
    private String parserBean;
    @Enumerated(EnumType.STRING) @Builder.Default private SiteStatus status = SiteStatus.READY;
    private LocalDateTime lastScanAt;
    private LocalDateTime lastUpdateAt;
    private LocalDateTime lastHealthCheckAt;
    @Column(columnDefinition = "TEXT") private String healthMessage;
    private Instant crawlerBlockedUntil;
    @Column(length = 500) private String crawlerBlockReason;
    @Column(columnDefinition = "TEXT") private String crawlerBlockUrl;
    @OneToOne(mappedBy = "site", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private CrawlerSiteRule rule;
    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;

    public void attachRule(CrawlerSiteRule value) {
        rule = value;
        if (value != null) value.setSite(this);
    }

    public boolean isManuallyFrozenAt(Instant now) {
        return Boolean.TRUE.equals(manuallyFrozen)
                && (manualFreezeUntil == null || manualFreezeUntil.isAfter(now));
    }

    public boolean isAccessBlockedAt(LocalTime time) {
        if (time == null || blockedAccessWindows == null || blockedAccessWindows.isEmpty()) {
            return false;
        }
        int minuteOfDay = time.getHour() * 60 + time.getMinute();
        return blockedAccessWindows.stream()
                .anyMatch(window -> window.containsMinute(minuteOfDay));
    }

    public enum ParserType { CONFIG, CUSTOM }
    public enum SiteStatus { READY, PAUSED, RULE_ERROR }
}
