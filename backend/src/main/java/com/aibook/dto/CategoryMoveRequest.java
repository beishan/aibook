package com.aibook.dto;

import lombok.Data;

/**
 * 移动分类请求。
 */
@Data
public class CategoryMoveRequest {

    private Long parentId;
    private Integer sortOrder;
}
