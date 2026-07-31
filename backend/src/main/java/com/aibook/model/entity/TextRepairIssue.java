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
 * TXT 内容修复问题记录
 */
@Entity
@Table(name = "text_repair_issues")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TextRepairIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联修复任务 ID */
    @Column(nullable = false)
    private Long taskId;

    /** 所属章节序号（从 0 开始，-1 表示全书级别） */
    @Builder.Default
    private Integer chapterIndex = -1;

    /** 问题类型 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepairIssueType type;

    /** 在全文中的起始偏移量 */
    private Integer startOffset;

    /** 在全文中的结束偏移量 */
    private Integer endOffset;

    /** 原始文本 */
    @Column(columnDefinition = "TEXT")
    private String originalText;

    /** 建议修复后文本 */
    @Column(columnDefinition = "TEXT")
    private String suggestedText;

    /** 修复原因 */
    @Column(columnDefinition = "TEXT")
    private String reason;

    /** 匹配规则 ID（可空） */
    private String ruleId;

    /** 置信度 0.0 ~ 1.0 */
    @Builder.Default
    private Double confidence = 0.0;

    /** 问题状态 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RepairIssueStatus status = RepairIssueStatus.PENDING;

    /** 操作来源 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RepairSource source = RepairSource.AUTO;

    /** 风险等级 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RiskLevel riskLevel = RiskLevel.LOW;

    /** 额外元数据 JSON（如候选编码列表、相似度信息等） */
    @Column(columnDefinition = "TEXT")
    private String metadataJson;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
