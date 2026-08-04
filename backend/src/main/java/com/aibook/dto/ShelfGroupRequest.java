package com.aibook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ShelfGroupRequest {

    @NotBlank(message = "分组名称不能为空")
    @Size(max = 80, message = "分组名称不能超过80个字符")
    private String name;

    @Size(max = 500, message = "分组描述不能超过500个字符")
    private String description;

    @Size(max = 16, message = "分组图标不合法")
    private String icon;

    @Size(max = 16, message = "分组颜色不合法")
    private String color;
}
