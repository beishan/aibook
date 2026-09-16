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
            @Min(500) @Max(120000) Integer retryBackoffMaxMillis,
            @Min(0) @Max(120000) Integer maxInlineRetryDelayMillis,
            @Min(1) @Max(64) Integer maxResponseSizeMb,
            @Min(0) @Max(10) Integer maxRedirects,
            @Min(1) @Max(16) Integer maxOriginConcurrency,
            @Min(1000) @Max(300000) Integer adaptiveDelayMaxMillis,
            @Min(10) @Max(86400) Integer circuitCooldownSeconds,
            @Min(60) @Max(604800) Integer accessDeniedCooldownSeconds,
            @Min(1) @Max(10080) Integer robotsCacheMinutes,
            @Min(1) @Max(1440) Integer robotsErrorCacheMinutes,
            Boolean softBlockDetectionEnabled,
            @Size(max = 500) String userAgent,
            @Size(max = 20000) String cookie,
            @Size(max = 20000) String headersJson) { }
}
