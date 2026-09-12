package com.aibook.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "crawler_discovery_pages", uniqueConstraints =
        @UniqueConstraint(name = "uk_crawler_discovery_page_site_name", columnNames = {"site_id", "page_name"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlerDiscoveryPage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private CrawlerSite site;
    @Column(name = "page_name", nullable = false, length = 100)
    private String pageName;
    @Column(nullable = false, length = 1000)
    private String pageUrl;
    @Builder.Default private Boolean autoScanEnabled = false;
    @Builder.Default private Integer scanIntervalMinutes = 360;
    @Builder.Default private Integer maxPages = 50;
    private LocalDateTime lastScanAt;
    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;
}
