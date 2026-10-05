package com.aibook.model.entity;

import jakarta.persistence.*;
import lombok.*;

/** Deduplicates successful chapter/task accounting across retries and application restarts. */
@Entity
@Table(name = "crawler_mihomo_receipts", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"executor_id", "event_key"})})
@Getter
@Setter
@NoArgsConstructor
public class CrawlerMihomoReceipt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "executor_id", nullable = false)
    private Long executorId;
    @Column(name = "event_key", nullable = false, length = 150)
    private String eventKey;
}
