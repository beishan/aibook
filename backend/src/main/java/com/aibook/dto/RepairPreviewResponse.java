package com.aibook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 修复预览内容响应（原文 vs 修复后对照）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepairPreviewResponse {

    /** 原始文本 */
    private String originalText;

    /** 修复后文本 */
    private String repairedText;

    /** 差异行列表 */
    private List<DiffLine> diffLines;

    /** 涉及的问题 ID 列表 */
    private List<Long> issueIds;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiffLine {
        /** ADDED / REMOVED / UNCHANGED / MODIFIED */
        private String type;
        private String originalLine;
        private String repairedLine;
    }
}
