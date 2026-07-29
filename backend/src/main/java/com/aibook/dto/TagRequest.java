package com.aibook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建或更新标签请求。
 */
@Data
public class TagRequest {

    @NotBlank(message = "标签名称不能为空")
    @Size(max = 30, message = "标签名称不能超过30个字符")
    private String name;

    @Pattern(
            regexp = "^#[0-9a-fA-F]{6}$",
            message = "标签颜色必须是 #RRGGBB 格式")
    private String color;
}
