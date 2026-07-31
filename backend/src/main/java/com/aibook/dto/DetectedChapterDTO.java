package com.aibook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 章节识别结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetectedChapterDTO {

    /** 章节序号（从 1 开始） */
    private Integer number;

    /** 原始编号文本（如 "一"、"001"） */
    private String originalNumber;

    /** 原始标题 */
    private String originalTitle;

    /** 规范化后的标题 */
    private String normalizedTitle;

    /** 章节类型 */
    private String type;

    /** 起始偏移量 */
    private Integer startOffset;

    /** 结束偏移量 */
    private Integer endOffset;

    /** 识别置信度 0.0 ~ 1.0 */
    private Double confidence;

    /** 正文字数 */
    private Integer wordCount;
}
