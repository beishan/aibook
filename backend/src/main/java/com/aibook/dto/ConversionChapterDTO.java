package com.aibook.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversionChapterDTO {
    private Integer index;
    /** TXT 中识别到的原始标题，用于重新套用格式规则和剔除正文中的标题行。 */
    private String sourceTitle;
    private String title;
    private Integer startIndex;
    private Integer endIndex;
    @Builder.Default private Boolean ignored = false;
}
