package com.aibook.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Persistent per-site task queue and dispatch policy. */
@Entity
@Table(name = "crawler_task_queues", uniqueConstraints =
        @UniqueConstraint(name = "uk_crawler_task_queue_site", columnNames = "site_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlerTaskQueue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private CrawlerSite site;

    @Builder.Default
    private Integer maxConcurrentTasks = 4;

    @Builder.Default
    private Integer taskIntervalSeconds = 0;

    private LocalDateTime lastTaskStartedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
