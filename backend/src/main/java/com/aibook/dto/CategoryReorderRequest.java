package com.aibook.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量分类排序请求。
 */
@Data
public class CategoryReorderRequest {

    @Valid
    @NotEmpty(message = "排序列表不能为空")
    private List<Item> items;

    @Data
    public static class Item {
        private Long id;
        private Integer sortOrder;
    }
}
