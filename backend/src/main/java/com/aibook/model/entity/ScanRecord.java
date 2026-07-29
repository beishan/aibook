package com.aibook.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一次目录扫描的持久化执行记录。
 */
@Entity
@Table(
        name = "scan_records",
        indexes = {
            @Index(name = "idx_scan_records_user_started", columnList = "user_id, started_at"),
            @Index(name = "idx_scan_records_directory", columnList = "directory_id")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String taskId;

    @Column(nullable = false)
    private Long directoryId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String directoryPath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(columnDefinition = "TEXT")
    private String message;

    private Integer totalCount;
    private Integer scannedCount;
    private Integer newBooks;
    private Integer skippedBooks;
    private Integer failedBooks;
    private Integer threadCount;
    private Long durationMs;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    @Column(columnDefinition = "TEXT")
    private String errorDetails;

    public enum Status {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED
    }
}
