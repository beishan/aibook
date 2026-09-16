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
    private static final String RETRY_BACKOFF_MAX = PREFIX + "retryBackoffMaxMillis";
    private static final String MAX_INLINE_RETRY_DELAY = PREFIX + "maxInlineRetryDelayMillis";
    private static final String MAX_RESPONSE_SIZE_MB = PREFIX + "maxResponseSizeMb";
    private static final String MAX_REDIRECTS = PREFIX + "maxRedirects";
    private static final String MAX_ORIGIN_CONCURRENCY = PREFIX + "maxOriginConcurrency";
    private static final String ADAPTIVE_DELAY_MAX = PREFIX + "adaptiveDelayMaxMillis";
    private static final String CIRCUIT_COOLDOWN = PREFIX + "circuitCooldownSeconds";
    private static final String ACCESS_DENIED_COOLDOWN = PREFIX + "accessDeniedCooldownSeconds";
    private static final String ROBOTS_CACHE = PREFIX + "robotsCacheMinutes";
    private static final String ROBOTS_ERROR_CACHE = PREFIX + "robotsErrorCacheMinutes";
    private static final String SOFT_BLOCK_DETECTION = PREFIX + "softBlockDetectionEnabled";
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
        values.put(RETRY_BACKOFF_MAX, Integer.toString(normalized.retryBackoffMaxMillis()));
        values.put(MAX_INLINE_RETRY_DELAY, Integer.toString(normalized.maxInlineRetryDelayMillis()));
        values.put(MAX_RESPONSE_SIZE_MB, Integer.toString(normalized.maxResponseSizeMb()));
        values.put(MAX_REDIRECTS, Integer.toString(normalized.maxRedirects()));
        values.put(MAX_ORIGIN_CONCURRENCY, Integer.toString(normalized.maxOriginConcurrency()));
        values.put(ADAPTIVE_DELAY_MAX, Integer.toString(normalized.adaptiveDelayMaxMillis()));
        values.put(CIRCUIT_COOLDOWN, Integer.toString(normalized.circuitCooldownSeconds()));
        values.put(ACCESS_DENIED_COOLDOWN, Integer.toString(normalized.accessDeniedCooldownSeconds()));
        values.put(ROBOTS_CACHE, Integer.toString(normalized.robotsCacheMinutes()));
        values.put(ROBOTS_ERROR_CACHE, Integer.toString(normalized.robotsErrorCacheMinutes()));
        values.put(SOFT_BLOCK_DETECTION, Boolean.toString(normalized.softBlockDetectionEnabled()));
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
                integer(values.get(RETRY_BACKOFF_MAX), 30000),
                integer(values.get(MAX_INLINE_RETRY_DELAY), 30000),
                integer(values.get(MAX_RESPONSE_SIZE_MB), 8),
                integer(values.get(MAX_REDIRECTS), 5),
                integer(values.get(MAX_ORIGIN_CONCURRENCY), 4),
                integer(values.get(ADAPTIVE_DELAY_MAX), 60000),
                integer(values.get(CIRCUIT_COOLDOWN), 900),
                integer(values.get(ACCESS_DENIED_COOLDOWN), 3600),
                integer(values.get(ROBOTS_CACHE), 360),
                integer(values.get(ROBOTS_ERROR_CACHE), 15),
                bool(values.get(SOFT_BLOCK_DETECTION), true),
                values.getOrDefault(USER_AGENT, ""),
                values.getOrDefault(COOKIE, ""),
                values.getOrDefault(HEADERS, "{}")));
    }

    private CrawlerRequestSettings normalize(CrawlerRequestSettings request) {
        int timeout = range(request.timeoutMillis(), 15000, 1000, 120000, "单次请求超时");
        int retry = range(request.retryCount(), 2, 0, 8, "单次请求重试次数");
        int consecutive = range(request.maxConsecutiveFailures(), 5, 1, 100, "任务连续请求失败上限");
        int retryBackoff = range(request.retryBackoffMaxMillis(), 30000, 500, 120000, "重试退避上限");
        int inlineRetry = range(request.maxInlineRetryDelayMillis(), 30000, 0, 120000, "原地重试等待上限");
        int responseSize = range(request.maxResponseSizeMb(), 8, 1, 64, "响应体积上限");
        int redirects = range(request.maxRedirects(), 5, 0, 10, "重定向次数上限");
        int originConcurrency = range(request.maxOriginConcurrency(), 4, 1, 16, "同源并发上限");
        int adaptiveDelay = range(request.adaptiveDelayMaxMillis(), 60000, 1000, 300000, "自适应延迟上限");
        int circuitCooldown = range(request.circuitCooldownSeconds(), 900, 10, 86400, "普通熔断冷却时间");
        int deniedCooldown = range(request.accessDeniedCooldownSeconds(), 3600, 60, 604800, "访问拒绝冷却时间");
        int robotsCache = range(request.robotsCacheMinutes(), 360, 1, 10080, "robots缓存时间");
        int robotsErrorCache = range(request.robotsErrorCacheMinutes(), 15, 1, 1440, "robots异常缓存时间");
        String userAgent = text(request.userAgent());
        String cookie = text(request.cookie());
        String headers = normalizeHeaders(request.headersJson());
        return new CrawlerRequestSettings(timeout, retry, consecutive, retryBackoff, inlineRetry,
                responseSize, redirects, originConcurrency, adaptiveDelay, circuitCooldown,
                deniedCooldown, robotsCache, robotsErrorCache,
                request.softBlockDetectionEnabled() == null || request.softBlockDetectionEnabled(),
                userAgent, cookie, headers);
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

    private boolean bool(String value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(value);
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
