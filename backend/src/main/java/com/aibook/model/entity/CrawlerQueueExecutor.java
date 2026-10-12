package com.aibook.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "crawler_queue_executors")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlerQueueExecutor {
    public enum ProxyMode { DEFAULT, SELECTED, MIHOMO }
    public enum SelectionStrategy { ORDERED, RANDOM }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "queue_id", nullable = false)
    private CrawlerTaskQueue queue;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean defaultExecutor;

    @Builder.Default
    private Boolean enabled = true;

    // NULL 保持旧执行器已加入队列的行为，新建执行器显式设为 false。
    @Builder.Default
    private Boolean assigned = true;

    @Builder.Default
    private Boolean deletionPending = false;

    @Column(nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ProxyMode proxyMode = ProxyMode.DEFAULT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SelectionStrategy selectionStrategy = SelectionStrategy.ORDERED;

    private Integer defaultProxyCooldownSeconds;
}
