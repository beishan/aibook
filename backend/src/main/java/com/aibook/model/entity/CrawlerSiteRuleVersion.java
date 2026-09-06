package com.aibook.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "crawler_site_rule_versions", uniqueConstraints =
        @UniqueConstraint(name = "uk_crawler_rule_version", columnNames = {"site_id", "version"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CrawlerSiteRuleVersion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false) private CrawlerSite site;
    @Column(nullable = false) private Integer version;
    @Column(nullable = false, columnDefinition = "TEXT") private String configJson;
    @Column(length = 300) private String changeSummary;
    @Builder.Default private Boolean enabled = false;
    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;
}
