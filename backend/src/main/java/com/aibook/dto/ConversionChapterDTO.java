package com.aibook.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversionChapterDTO {
    private Integer index;
    private String title;
    private Integer startIndex;
    private Integer endIndex;
    @Builder.Default private Boolean ignored = false;
}
