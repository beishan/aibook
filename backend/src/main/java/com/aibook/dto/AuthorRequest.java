package com.aibook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 手动新增作者请求。 */
@Data
public class AuthorRequest {
    @NotBlank(message = "作者名称不能为空")
    @Size(max = 255, message = "作者名称不能超过255个字符")
    private String name;
}
