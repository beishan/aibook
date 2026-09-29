package com.aibook.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "crawler_queue_executor_proxies",
        uniqueConstraints = @UniqueConstraint(name = "uk_crawler_executor_proxy",
                columnNames = {"executor_id", "proxy_config_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlerQueueExecutorProxy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "executor_id", nullable = false)
    private CrawlerQueueExecutor executor;

    @Column(name = "proxy_config_id", nullable = false)
    private Long proxyConfigId;

    @Column(nullable = false)
    private int sortOrder;

    private Integer cooldownSeconds;
}
