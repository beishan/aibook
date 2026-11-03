package com.aibook.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "crawler_task_log_retention", uniqueConstraints =
        @UniqueConstraint(name = "uk_crawler_log_retention_user_type", columnNames = {"user_id", "task_type"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrawlerTaskLogRetention {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private User user;
    @Column(name = "task_type", nullable = false, length = 32)
    private String taskType;
    @Column(nullable = false)
    private Integer retentionDays;
    @Column(nullable = false)
    private Boolean automatic;
}
