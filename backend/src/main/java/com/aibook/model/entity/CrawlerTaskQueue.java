package com.aibook.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Persistent per-site or free task queue and dispatch policy. */
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

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "site_id")
    private CrawlerSite site;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(length = 100)
    private String queueName;

    @Builder.Default
    private Integer maxConcurrentTasks = 4;

    @Builder.Default
    private Integer taskIntervalSeconds = 0;

    private Integer sortOrder;

    private LocalDateTime lastTaskStartedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
