package com.aibook.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(name = "crawler_scan_results", indexes =
        @Index(name = "idx_crawler_scan_result_task", columnList = "task_id,id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlerScanResult {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CrawlerTask task;
    private Long crawlerBookId;
    @Column(nullable = false, length = 500)
    private String bookName;
    @Column(nullable = false, length = 1000)
    private String bookUrl;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResultStatus resultStatus;
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    @CreationTimestamp private LocalDateTime createdAt;

    public enum ResultStatus { NEW, DUPLICATE, BLACKLISTED, FAILED }
}
