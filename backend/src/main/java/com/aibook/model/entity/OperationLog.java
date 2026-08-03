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
import org.hibernate.annotations.CreationTimestamp;

/**
 * 用户关键操作日志。
 */
@Entity
@Table(
        name = "operation_logs",
        indexes = {
            @Index(name = "idx_operation_logs_user_created", columnList = "user_id, created_at"),
            @Index(name = "idx_operation_logs_user_action", columnList = "user_id, action")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Action action;

    private Long bookId;

    @Column(length = 500)
    private String bookTitle;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(columnDefinition = "TEXT")
    private String details;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum Action {
        IMPORT_BOOK,
        OPEN_BOOK,
        DELETE_BOOK,
        PERMANENTLY_DELETE_BOOK,
        RESTORE_BOOK,
        CREATE_USER,
        UPDATE_USER,
        DELETE_USER,
        RESET_PASSWORD,
        UPDATE_PROFILE,
        UPDATE_AVATAR
    }
}
