package com.aibook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 修复结果报告
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepairReportDTO {

    /** 检测章节数 */
    private Integer detectedChapters;

    /** 修复乱码数 */
    private Integer fixedEncoding;

    /** 删除广告数 */
    private Integer removedAds;

    /** 统一章节标题数 */
    private Integer normalizedChapters;

    /** 修复错误换行数 */
    private Integer fixedLineBreaks;

    /** 删除重复段落数 */
    private Integer removedDuplicates;

    /** 清理不可见字符数 */
    private Integer cleanedInvisibleChars;

    /** 统一标点数 */
    private Integer normalizedPunctuation;

    /** 异常问题列表 */
    private List<AnomalyItem> anomalies;

    /** 未确认问题数 */
    private Integer unconfirmedCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnomalyItem {
        private String type;
        private String description;
        private Integer count;
    }
}
