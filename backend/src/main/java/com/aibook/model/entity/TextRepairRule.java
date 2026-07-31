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
 * TXT 内容修复规则（广告规则、乱码规则等）
 */
@Entity
@Table(name = "text_repair_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TextRepairRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 规则名称 */
    @Column(nullable = false)
    private String name;

    /** 问题类型 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepairIssueType type;

    /** 匹配表达式（正则或关键词） */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String pattern;

    /** 匹配范围 */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private MatchScope matchScope = MatchScope.LINE;

    /** 处理方式 */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RepairAction action = RepairAction.DELETE_LINE;

    /** 替换内容（当 action 为 REPLACE 时使用） */
    private String replacement;

    /** 风险等级 */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RiskLevel riskLevel = RiskLevel.LOW;

    /** 是否启用 */
    @Builder.Default
    private Boolean enabled = true;

    /** 是否系统内置规则 */
    @Builder.Default
    private Boolean systemRule = false;

    /** 规则作用范围：CURRENT_BOOK / ALL_BOOKS / TEMPLATE */
    @Builder.Default
    private String scope = "ALL_BOOKS";

    /** 关联模板 ID（scope 为 TEMPLATE 时使用） */
    private Long templateId;

    /** 所属用户 ID（可空，为空表示全局规则） */
    private Long userId;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
