package com.aibook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 编码检测结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EncodingDetectResult {

    /** 检测到的编码名称 */
    private String encoding;

    /** 置信度 0.0 ~ 1.0 */
    private Double confidence;

    /** 异常字符数量 */
    private Integer anomalyCount;

    /** 是否存在 BOM */
    private Boolean hasBom;

    /** BOM 类型：UTF-8 / UTF-16LE / UTF-16BE / NONE */
    private String bomType;

    /** 解码后的预览文本（前 N 个字符） */
    private String previewText;

    /** 是否检测到乱码 */
    private Boolean hasGarbled;

    /** 乱码类型描述 */
    private String garbledType;

    /** 候选编码列表 */
    private java.util.List<String> candidateEncodings;
}
