package com.aibook.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 书籍批量操作请求。
 */
@Data
public class BookIdsRequest {

    @NotEmpty(message = "书籍ID列表不能为空")
    private List<Long> bookIds;
}
