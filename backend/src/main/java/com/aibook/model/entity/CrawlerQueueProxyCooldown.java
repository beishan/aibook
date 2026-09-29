package com.aibook.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "crawler_queue_proxy_cooldowns",
        uniqueConstraints = @UniqueConstraint(name = "uk_crawler_queue_proxy_cooldown",
                columnNames = {"queue_id", "proxy_key"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlerQueueProxyCooldown {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "queue_id", nullable = false)
    private CrawlerTaskQueue queue;

    @Column(name = "proxy_key", nullable = false, length = 100)
    private String proxyKey;

    @Column(nullable = false)
    private Instant blockedUntil;

    @Column(length = 300)
    private String reason;
}
