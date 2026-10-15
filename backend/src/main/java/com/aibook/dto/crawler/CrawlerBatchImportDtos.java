package com.aibook.dto.crawler;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class CrawlerBatchImportDtos {
    private CrawlerBatchImportDtos() {}

    public record Request(
            @NotEmpty @Size(max = 1000) List<@NotNull @Positive Long> bookIds,
            @NotEmpty List<@NotNull @Pattern(regexp = "(?i)STRUCTURED|TXT|EPUB") String> formats) {}

    public record ItemResult(Long crawlerBookId, Long bookId, boolean success, String reason) {}

    public record JobView(String id, String status, int total, int completed,
            List<ItemResult> results) {}
}
