package com.aibook.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CategoryBatchRequest(
        @NotEmpty @Size(max = 1000) List<@NotNull Long> ids,
        @NotNull Action action, Long targetCategoryId) {
    public enum Action { ENABLE, DISABLE, DELETE }
}
