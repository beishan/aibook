package com.aibook.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 账户按书籍版本、自然日记录的阅读活动。
 *
 * <p>使用标量外键值可以让历史部署通过 Hibernate ddl-auto 平滑建表；删除账户或版本时由
 * 对应服务显式清理。唯一约束配合 PostgreSQL upsert，保证并发心跳的增量不会互相覆盖。</p>
 */
@Entity
@Table(name = "reading_daily_activity",
        indexes = @Index(name = "idx_reading_daily_activity_user_date",
                columnList = "user_id, reading_date"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_reading_daily_activity_user_version_day",
                columnNames = {"user_id", "version_id", "reading_date"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadingDailyActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "reading_date", nullable = false)
    private LocalDate readingDate;

    @Column(name = "reading_time_seconds", nullable = false)
    @Builder.Default
    private Long readingTimeSeconds = 0L;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
