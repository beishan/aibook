package com.aibook.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量刮削请求 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchScrapeRequest {

    /**
     * 要刮削的书籍ID列表
     */
    @NotEmpty(message = "书籍ID列表不能为空")
    private List<Long> bookIds;

    /**
     * 是否强制更新已有字段（默认false，只填充空字段）
     */
    private boolean forceUpdate = false;
}
