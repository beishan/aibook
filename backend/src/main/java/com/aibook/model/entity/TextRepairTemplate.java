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
 * TXT 内容修复配置模板
 */
@Entity
@Table(name = "text_repair_templates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TextRepairTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 模板名称 */
    @Column(nullable = false)
    private String name;

    /** 模板描述 */
    private String description;

    /** 修复模式 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepairMode repairMode;

    /** 启用的修复项 JSON（编码、广告、章节等开关） */
    @Column(columnDefinition = "TEXT")
    private String enabledItemsJson;

    /** 章节输出格式，如 "第{number}章 {title}" */
    @Builder.Default
    private String chapterFormat = "第{number}章 {title}";

    /** 段落缩进方式：NONE / FULL_WIDTH_SPACE / HALF_SPACE / FOUR_SPACE / KEEP */
    @Builder.Default
    private String indentStyle = "FULL_WIDTH_SPACE";

    /** 段落间空行数量：0 / 1 / 2 / -1(保持原样) */
    @Builder.Default
    private Integer blankLineCount = 1;

    /** 标点统一开关 */
    @Builder.Default
    private Boolean punctuationNormalize = false;

    /** 繁简转换：NONE / T2S / S2T / T2TW / T2HK */
    @Builder.Default
    private String traditionalSimplified = "NONE";

    /** 超短章节字数阈值，默认 100 */
    @Builder.Default
    private Integer minChapterWords = 100;

    /** 超长章节字数阈值，默认 30000 */
    @Builder.Default
    private Integer maxChapterWords = 30000;

    /** 自动处理置信度阈值（高于此值可自动应用） */
    @Builder.Default
    private Double autoApplyThreshold = 0.8;

    /** 是否系统内置模板 */
    @Builder.Default
    private Boolean systemTemplate = false;

    /** 所属用户 ID */
    private Long userId;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
