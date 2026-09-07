package com.aibook.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public final class CrawlerSettingsDtos {
    private CrawlerSettingsDtos() { }

    public record CrawlerRequestSettings(
            @Min(1000) @Max(120000) Integer timeoutMillis,
            @Min(0) @Max(8) Integer retryCount,
            @Min(1) @Max(100) Integer maxConsecutiveFailures,
            @Size(max = 500) String userAgent,
            @Size(max = 20000) String cookie,
            @Size(max = 20000) String headersJson) { }
}
