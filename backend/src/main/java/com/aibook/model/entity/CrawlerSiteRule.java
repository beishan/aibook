package com.aibook.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "crawler_site_rules")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CrawlerSiteRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false, unique = true)
    private CrawlerSite site;
    @Builder.Default private Integer ruleVersion = 1;
    private String titleSelector;
    private String authorSelector;
    private String coverSelector;
    private String descriptionSelector;
    private String categorySelector;
    private String statusSelector;
    private String latestChapterSelector;
    private String chapterListUrlSelector;
    @Column(nullable = false) private String chapterItemSelector;
    private String chapterTitleSelector;
    @Column(nullable = false) private String chapterUrlSelector;
    private String contentTitleSelector;
    @Column(nullable = false) private String contentSelector;
    @Column(columnDefinition = "TEXT") private String removeSelectors;
    @Column(columnDefinition = "TEXT") private String regexReplacementsJson;
    @Builder.Default private Integer minChapterLength = 100;
    @Builder.Default private Boolean enabled = true;
    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;
}
