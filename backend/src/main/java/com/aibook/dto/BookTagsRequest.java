package com.aibook.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 单本或批量设置书籍标签请求。
 */
@Data
public class BookTagsRequest {

    private List<Long> bookIds;

    @NotNull(message = "标签ID列表不能为空")
    private List<Long> tagIds;

    /**
     * 批量操作方式：ADD、REMOVE 或 REPLACE。
     */
    private String mode = "ADD";

}
