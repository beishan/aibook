package com.aibook.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "crawler_blacklist_screen_jobs", indexes =
        @Index(name = "idx_blacklist_screen_owner_created", columnList = "user_id,created_at"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlerBlacklistScreenJob {
    @Id
    @Builder.Default
    private String id = UUID.randomUUID().toString();
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;
    private Long siteId;
    /** Null on legacy jobs means MANAGED. */
    @Column(length = 20)
    private String scope;
    @Builder.Default
    private String status = "RUNNING";
    private long total;
    private long processed;
    private long matched;
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime finishedAt;
}
