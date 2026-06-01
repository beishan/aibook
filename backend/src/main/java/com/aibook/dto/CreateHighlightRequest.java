package com.aibook.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建高亮请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateHighlightRequest {

    @NotBlank(message = "cfiRange 不能为空")
    private String cfiRange;

    @NotBlank(message = "text 不能为空")
    private String text;

    private String color;

    private String chapter;

    private String note;
}
