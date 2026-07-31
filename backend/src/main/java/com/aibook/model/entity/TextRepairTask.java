package com.aibook.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * TXT 内容修复任务
 */
@Entity
@Table(name = "text_repair_tasks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TextRepairTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联书籍 ID */
    @Column(nullable = false)
    private Long bookId;

    /** 关联书籍版本 ID（可空，为空时使用主版本） */
    private Long versionId;

    /** 修复模式 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepairMode repairMode;

    /** 任务状态：SCANNING / SCANNED / REPAIRING / COMPLETED / CANCELLED */
    @Column(nullable = false)
    @Builder.Default
    private String status = "SCANNING";

    /** 原始内容版本标识（文件哈希或时间戳） */
    private String originalContentVersion;

    /** 修复后内容版本标识 */
    private String repairedContentVersion;

    /** 修复配置 JSON */
    @Column(columnDefinition = "TEXT")
    private String optionsJson;

    /** 修复报告 JSON */
    @Column(columnDefinition = "TEXT")
    private String reportJson;

    /** 问题总数 */
    @Builder.Default
    private Integer totalIssueCount = 0;

    /** 待处理问题数 */
    @Builder.Default
    private Integer pendingIssueCount = 0;

    /** 已接受问题数 */
    @Builder.Default
    private Integer acceptedIssueCount = 0;

    /** 已拒绝问题数 */
    @Builder.Default
    private Integer rejectedIssueCount = 0;

    /** 已忽略问题数 */
    @Builder.Default
    private Integer ignoredIssueCount = 0;

    /** 已应用问题数 */
    @Builder.Default
    private Integer appliedIssueCount = 0;

    /** 所属用户 ID */
    @Column(nullable = false)
    private Long userId;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
