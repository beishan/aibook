package com.aibook.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "crawler_site_activity_retention", uniqueConstraints =
        @UniqueConstraint(name = "uk_site_activity_retention_site", columnNames = "site_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlerSiteActivityRetention {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CrawlerSite site;

    @Column(nullable = false)
    private Integer retentionDays;

    @Column(nullable = false)
    private Boolean automatic;
}
