package com.aibook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建或更新分类请求。
 */
@Data
public class CategoryRequest {

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称不能超过50个字符")
    private String name;

    @Size(max = 255, message = "分类描述不能超过255个字符")
    private String description;

    private Long parentId;
    private Integer sortOrder;
    private Boolean enabled;
}
