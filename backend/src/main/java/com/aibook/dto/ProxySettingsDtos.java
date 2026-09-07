package com.aibook.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class ProxySettingsDtos {
    private ProxySettingsDtos() { }

    public record SystemProxyRequest(String name, String url, Boolean enabled, Integer priority) { }

    public record SystemProxyOrderRequest(List<Long> ids) { }

    public record SystemProxyView(Long id, String name, String url, boolean enabled, int priority,
            LocalDateTime createdAt, LocalDateTime updatedAt) { }

    public record CrawlerProxyRequest(String name, String url, Long systemProxyId,
            Boolean enabled, Integer priority) { }

    public record CrawlerProxyOrderRequest(List<Long> ids) { }

    public record CrawlerProxyView(Long id, String sourceType, String name, String url,
            Long systemProxyId, String systemProxyName, boolean enabled,
            boolean sourceAvailable, boolean effectiveEnabled, int priority,
            LocalDateTime createdAt, LocalDateTime updatedAt) { }
}
