package com.aibook.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class ShelfBookOrderRequest {

    private Long groupId;

    @NotNull(message = "书籍顺序不能为空")
    private List<Long> bookIds;
}
