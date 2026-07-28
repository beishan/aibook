package com.aibook.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 单本或批量设置书籍分类请求。
 */
@Data
public class BookCategoryRequest {

    @NotEmpty(message = "书籍ID列表不能为空")
    private List<Long> bookIds;

    private Long categoryId;
}
