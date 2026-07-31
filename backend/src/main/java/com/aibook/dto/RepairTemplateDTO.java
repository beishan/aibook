package com.aibook.dto;

import com.aibook.model.entity.RepairMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 修复模板 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepairTemplateDTO {

    private Long id;
    private String name;
    private String description;
    private RepairMode repairMode;
    private String enabledItemsJson;
    private String chapterFormat;
    private String indentStyle;
    private Integer blankLineCount;
    private Boolean punctuationNormalize;
    private String traditionalSimplified;
    private Integer minChapterWords;
    private Integer maxChapterWords;
    private Double autoApplyThreshold;
    private Boolean systemTemplate;
    private Long userId;
}
