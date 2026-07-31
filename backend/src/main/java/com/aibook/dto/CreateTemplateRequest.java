package com.aibook.dto;

import com.aibook.model.entity.RepairMode;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建/更新修复模板请求
 */
@Data
public class CreateTemplateRequest {

    @NotBlank
    private String name;

    private String description;

    private RepairMode repairMode;

    private String enabledItemsJson;

    private String chapterFormat = "第{number}章 {title}";

    private String indentStyle = "FULL_WIDTH_SPACE";

    private Integer blankLineCount = 1;

    private Boolean punctuationNormalize = false;

    private String traditionalSimplified = "NONE";

    private Integer minChapterWords = 100;

    private Integer maxChapterWords = 30000;

    private Double autoApplyThreshold = 0.8;
}
