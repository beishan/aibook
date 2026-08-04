package com.aibook.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class ShelfGroupOrderRequest {

    @NotNull(message = "分组顺序不能为空")
    private List<Long> groupIds;
}
