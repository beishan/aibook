package com.aibook.service.crawler;

import com.aibook.dto.CrawlerSettingsDtos.CrawlerRequestSettings;
import com.aibook.model.entity.CrawlerSite;
import com.aibook.service.CrawlerSettingsService;
import com.aibook.service.ProxySettingsService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.net.*;
import java.net.http.*;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
public class CrawlerHttpClient {
    private final ObjectMapper objectMapper;
    private final ProxySettingsService proxySettingsService;
    private final CrawlerSettingsService crawlerSettingsService;
    private final Map<Long, AtomicLong> nextRequests = new ConcurrentHashMap<>();
    private final Map<Long, Semaphore> concurrencyGates = new ConcurrentHashMap<>();

    public FetchResult get(CrawlerSite site, String url) throws Exception {
        return get(site, url, null, null);
    }

    public FetchResult get(CrawlerSite site, String url, String etag, String lastModified) throws Exception {
        URI uri = validateSiteUrl(site, url);
        List<String> proxies = proxyUrls(site);
        CrawlerRequestSettings settings = requestSettings();
        int attempts = Math.max(Math.max(1, settings.retryCount() + 1), proxies.size());
        Exception last = null;
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                String proxyUrl = proxies.isEmpty() ? null : proxies.get(attempt % proxies.size());
                TimedResponse timed = sendFollowingSafeRedirects(
                        site, uri, etag, lastModified, proxyUrl, settings);
                HttpResponse<byte[]> response = timed.response();
                if (response.statusCode() == 304) return new FetchResult("", 304, timed.durationMillis(), etag, lastModified);
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    Charset charset = Charset.forName(defaultString(site.getEncoding(), "UTF-8"));
                    return new FetchResult(new String(response.body(), charset), response.statusCode(), timed.durationMillis(),
                            response.headers().firstValue("ETag").orElse(null),
                            response.headers().firstValue("Last-Modified").orElse(null));
                }
                if (response.statusCode() == 403) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "源站拒绝访问（HTTP 403），系统不会尝试绕过访问控制");
                if (response.statusCode() != 429 && response.statusCode() < 500) throw new IllegalStateException("源站返回 HTTP " + response.statusCode());
                last = new IllegalStateException("源站返回 HTTP " + response.statusCode());
                Thread.sleep(Math.min(8000L, 1000L << attempt));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw exception;
            } catch (Exception exception) { last = exception; }
        }
        throw last == null ? new IllegalStateException("请求失败") : last;
    }

    private TimedResponse sendFollowingSafeRedirects(CrawlerSite site, URI original, String etag,
            String lastModified, String proxyUrl, CrawlerRequestSettings settings) throws Exception {
        Semaphore gate = concurrencyGates.computeIfAbsent(site.getId(),
                ignored -> new Semaphore(Math.max(1, value(site.getMaxConcurrency(), 1)), true));
        gate.acquire();
        try {
            return sendFollowingSafeRedirectsWithinGate(
                    site, original, etag, lastModified, proxyUrl, settings);
        } finally {
            gate.release();
        }
    }

    private TimedResponse sendFollowingSafeRedirectsWithinGate(CrawlerSite site, URI original, String etag,
            String lastModified, String proxyUrl, CrawlerRequestSettings settings) throws Exception {
        URI current = original;
        long duration = 0;
        for (int redirects = 0; redirects <= 5; redirects++) {
            throttle(site);
            HttpRequest.Builder request = HttpRequest.newBuilder(current)
                    .timeout(Duration.ofMillis(settings.timeoutMillis()))
                    .GET().header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6")
                    .header("User-Agent", defaultString(settings.userAgent(),
                            "AiBookCrawler/1.0 (+private library; authorized content only)"));
            if (etag != null && !etag.isBlank()) request.header("If-None-Match", etag);
            if (lastModified != null && !lastModified.isBlank()) request.header("If-Modified-Since", lastModified);
            if (!settings.cookie().isBlank()) request.header("Cookie", settings.cookie());
            applyHeaders(request, settings.headersJson());
            long started = System.nanoTime();
            HttpResponse<byte[]> response = client(settings.timeoutMillis(), proxyUrl)
                    .send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            duration += (System.nanoTime() - started) / 1_000_000;
            if (response.statusCode() < 300 || response.statusCode() >= 400) return new TimedResponse(response, duration);
            String location = response.headers().firstValue("Location")
                    .orElseThrow(() -> new IllegalStateException("源站重定向缺少 Location"));
            current = validateSiteUrl(site, current.resolve(location).toString());
        }
        throw new IllegalStateException("源站重定向次数过多");
    }

    public URI validateSiteUrl(CrawlerSite site, String url) {
        try {
            URI base = URI.create(site.getBaseUrl());
            URI target = URI.create(url).isAbsolute() ? URI.create(url) : base.resolve(url);
            if (!Set.of("http", "https").contains(target.getScheme()) || target.getUserInfo() != null) throw new IllegalArgumentException();
            String baseHost = Objects.toString(base.getHost(), "").toLowerCase(Locale.ROOT);
            String targetHost = Objects.toString(target.getHost(), "").toLowerCase(Locale.ROOT);
            if (baseHost.isBlank() || !(targetHost.equals(baseHost) || targetHost.endsWith("." + baseHost))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "采集 URL 必须属于网站配置的域名");
            }
            return target;
        } catch (ResponseStatusException exception) { throw exception; }
        catch (Exception exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "网站地址或采集 URL 无效"); }
    }

    private HttpClient client(int timeoutMillis, String proxyUrl) {
        HttpClient.Builder builder = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofMillis(timeoutMillis));
        if (proxyUrl != null && !proxyUrl.isBlank()) {
            URI proxy = URI.create(proxyUrl.contains("://") ? proxyUrl : "http://" + proxyUrl);
            builder.proxy(ProxySelector.of(new InetSocketAddress(proxy.getHost(), proxy.getPort())));
        }
        return builder.build();
    }

    List<String> proxyUrls(CrawlerSite site) {
        if (site.getProxy() != null && !site.getProxy().isBlank()) return List.of(site.getProxy());
        return proxySettingsService.activeCrawlerProxyUrls();
    }

    int maxConsecutiveFailures() { return requestSettings().maxConsecutiveFailures(); }

    private CrawlerRequestSettings requestSettings() {
        CrawlerRequestSettings settings = crawlerSettingsService.settings();
        return settings == null ? new CrawlerRequestSettings(15000, 2, 5, "", "", "{}") : settings;
    }

    private void throttle(CrawlerSite site) throws InterruptedException {
        long now = System.currentTimeMillis();
        long interval = value(site.getRequestIntervalMillis(), 1500) +
                (value(site.getRandomDelayMillis(), 1000) == 0 ? 0 : new Random().nextInt(value(site.getRandomDelayMillis(), 1000) + 1));
        AtomicLong gate = nextRequests.computeIfAbsent(site.getId(), ignored -> new AtomicLong());
        long slot;
        do { slot = gate.get(); } while (!gate.compareAndSet(slot, Math.max(now, slot) + interval));
        long wait = slot - now;
        if (wait > 0) Thread.sleep(wait);
    }

    private void applyHeaders(HttpRequest.Builder request, String json) throws Exception {
        if (json == null || json.isBlank()) return;
        Map<String, String> headers = objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() { });
        for (var entry : headers.entrySet()) {
            String name = entry.getKey();
            if (!Set.of("host", "content-length", "connection", "authorization", "cookie").contains(name.toLowerCase(Locale.ROOT)))
                request.header(name, entry.getValue());
        }
    }

    private int value(Integer value, int fallback) { return value == null ? fallback : value; }
    private String defaultString(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    public record FetchResult(String html, int statusCode, long durationMillis, String etag, String lastModified) { }
    private record TimedResponse(HttpResponse<byte[]> response, long durationMillis) { }
}
