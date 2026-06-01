package com.aibook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新高亮请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateHighlightRequest {

    private String text;

    private String color;

    private String chapter;

    private String note;
}
