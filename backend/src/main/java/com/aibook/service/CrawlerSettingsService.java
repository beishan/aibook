package com.aibook.service;

import com.aibook.dto.CrawlerSettingsDtos.CrawlerRequestSettings;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CrawlerSettingsService {
    static final String PREFIX = "crawler.request.";
    private static final String TIMEOUT = PREFIX + "timeoutMillis";
    private static final String RETRY = PREFIX + "retryCount";
    private static final String MAX_CONSECUTIVE_FAILURES = PREFIX + "maxConsecutiveFailures";
    private static final String USER_AGENT = PREFIX + "userAgent";
    private static final String COOKIE = PREFIX + "cookie";
    private static final String HEADERS = PREFIX + "headersJson";
    private static final String MAX_CONCURRENT_TASKS = "crawler.task.maxConcurrentTasks";

    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper;
    private volatile CrawlerRequestSettings cached;

    public CrawlerRequestSettings settings() {
        CrawlerRequestSettings value = cached;
        if (value != null) return value;
        synchronized (this) {
            if (cached == null) cached = from(systemConfigService.getConfigsByPrefix(PREFIX));
            return cached;
        }
    }

    public synchronized CrawlerRequestSettings update(CrawlerRequestSettings request) {
        CrawlerRequestSettings normalized = normalize(request);
        Map<String, String> values = new LinkedHashMap<>();
        values.put(TIMEOUT, Integer.toString(normalized.timeoutMillis()));
        values.put(RETRY, Integer.toString(normalized.retryCount()));
        values.put(MAX_CONSECUTIVE_FAILURES, Integer.toString(normalized.maxConsecutiveFailures()));
        values.put(USER_AGENT, normalized.userAgent());
        values.put(COOKIE, normalized.cookie());
        values.put(HEADERS, normalized.headersJson());
        systemConfigService.saveConfigs(values);
        cached = normalized;
        return normalized;
    }

    public int maxConcurrentTasks() {
        return range(systemConfigService.getIntConfig(MAX_CONCURRENT_TASKS, 4), 4, 1, 16,
                "采集任务并行数量");
    }

    public int updateMaxConcurrentTasks(Integer value) {
        int normalized = range(value, 4, 1, 16, "采集任务并行数量");
        systemConfigService.saveConfig(MAX_CONCURRENT_TASKS, Integer.toString(normalized),
                "采集任务全局最大并行数量");
        return normalized;
    }

    private CrawlerRequestSettings from(Map<String, String> values) {
        return normalize(new CrawlerRequestSettings(
                integer(values.get(TIMEOUT), 15000),
                integer(values.get(RETRY), 2),
                integer(values.get(MAX_CONSECUTIVE_FAILURES), 5),
                values.getOrDefault(USER_AGENT, ""),
                values.getOrDefault(COOKIE, ""),
                values.getOrDefault(HEADERS, "{}")));
    }

    private CrawlerRequestSettings normalize(CrawlerRequestSettings request) {
        int timeout = range(request.timeoutMillis(), 15000, 1000, 120000, "单次请求超时");
        int retry = range(request.retryCount(), 2, 0, 8, "单次请求重试次数");
        int consecutive = range(request.maxConsecutiveFailures(), 5, 1, 100, "任务连续请求失败上限");
        String userAgent = text(request.userAgent());
        String cookie = text(request.cookie());
        String headers = normalizeHeaders(request.headersJson());
        return new CrawlerRequestSettings(timeout, retry, consecutive, userAgent, cookie, headers);
    }

    private String normalizeHeaders(String value) {
        if (value == null || value.isBlank()) return "{}";
        try {
            Map<String, String> headers = objectMapper.readValue(value,
                    new TypeReference<LinkedHashMap<String, String>>() { });
            return objectMapper.writeValueAsString(headers);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "自定义 Header 必须是字符串键值对 JSON", exception);
        }
    }

    private int integer(String value, int fallback) {
        try { return value == null ? fallback : Integer.parseInt(value); }
        catch (NumberFormatException exception) { return fallback; }
    }

    private int range(Integer value, int fallback, int min, int max, String name) {
        int normalized = value == null ? fallback : value;
        if (normalized < min || normalized > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    name + "必须在 " + min + " 到 " + max + " 之间");
        }
        return normalized;
    }

    private String text(String value) { return value == null ? "" : value.trim(); }
}
