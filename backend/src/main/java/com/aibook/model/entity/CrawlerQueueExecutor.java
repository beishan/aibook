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

    // 仅保留用于旧配置迁移；公共执行器不再归属创建队列。
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "queue_id")
    private CrawlerTaskQueue queue;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private User user;

    public Long ownerId() {
        if (user != null) return user.getId();
        if (queue == null) return null;
        return queue.getUser() != null ? queue.getUser().getId()
                : queue.getSite() == null ? null : queue.getSite().getUser().getId();
    }

    public java.util.Set<Long> assignedQueueIds() {
        var ids = new java.util.LinkedHashSet<>(sharedQueueIds);
        if (queue != null && !Boolean.FALSE.equals(assigned)) ids.add(queue.getId());
        return ids;
    }

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "crawler_executor_shared_queues",
            joinColumns = @JoinColumn(name = "executor_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"executor_id", "queue_id"}))
    @Column(name = "queue_id", nullable = false)
    @Builder.Default
    private java.util.Set<Long> sharedQueueIds = new java.util.LinkedHashSet<>();

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
