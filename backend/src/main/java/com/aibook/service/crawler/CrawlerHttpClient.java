package com.aibook.service.crawler;

import com.aibook.dto.CrawlerSettingsDtos.CrawlerRequestSettings;
import com.aibook.model.entity.CrawlerSite;
import com.aibook.repository.CrawlerSiteRepository;
import com.aibook.service.CrawlerSettingsService;
import com.aibook.service.ProxySettingsService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.net.http.*;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class CrawlerHttpClient {
    private static final String DEFAULT_USER_AGENT =
            "AiBookCrawler/1.0 (+private library; authorized content only)";
    private static final Pattern CHALLENGE_TITLE = Pattern.compile(
            "(?is)<title[^>]*>\\s*(?:just a moment|access denied|安全验证|访问验证|验证码|请求过于频繁|访问过于频繁)[^<]*</title>");

    private final ObjectMapper objectMapper;
    private final ProxySettingsService proxySettingsService;
    private final CrawlerSettingsService crawlerSettingsService;
    private final CrawlerSiteRepository crawlerSiteRepository;
    private final Map<Long, AtomicLong> siteNextRequests = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> originNextRequests = new ConcurrentHashMap<>();
    private final Map<Long, AdjustableConcurrencyGate> concurrencyGates = new ConcurrentHashMap<>();
    private final Map<String, AdjustableConcurrencyGate> originConcurrencyGates = new ConcurrentHashMap<>();
    private final Map<String, RobotsCacheEntry> robotsCache = new ConcurrentHashMap<>();
    private final Map<String, Object> robotsLocks = new ConcurrentHashMap<>();
    private final Map<Long, CircuitState> circuitStates = new ConcurrentHashMap<>();
    private final Map<Long, AdaptiveDelay> adaptiveDelays = new ConcurrentHashMap<>();
    private final Map<HttpClientKey, HttpClient> clients = new ConcurrentHashMap<>();

    public FetchResult get(CrawlerSite site, String url) throws Exception {
        return get(site, url, null, null);
    }

    public FetchResult get(CrawlerSite site, String url, String etag, String lastModified) throws Exception {
        URI uri = validateSiteUrl(site, url);
        CrawlerRequestSettings settings = requestSettings();
        ensureCircuitClosed(site);
        enforceRobots(site, uri, settings);
        List<String> proxies = proxyUrls(site);
        int attempts = Math.max(1, settings.retryCount() + 1);
        int proxyIndex = 0;
        Exception last = null;
        for (int attempt = 0; attempt < attempts; attempt++) {
            long retryDelay = retryBackoffMillis(attempt, settings.retryBackoffMaxMillis());
            try {
                String proxyUrl = proxies.isEmpty() ? null : proxies.get(proxyIndex % proxies.size());
                TimedResponse timed = sendFollowingSafeRedirects(
                        site, uri, etag, lastModified, proxyUrl, settings);
                NetworkResponse response = timed.response();
                if (response.statusCode() == 304) {
                    recordSuccess(site);
                    return new FetchResult("", 304, timed.durationMillis(), etag, lastModified);
                }
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    validateContentType(response);
                    Charset charset = Charset.forName(defaultString(site.getEncoding(), "UTF-8"));
                    String html = new String(response.body(), charset);
                    Optional<String> softBlock = settings.softBlockDetectionEnabled()
                            ? detectSoftBlock(html) : Optional.empty();
                    if (softBlock.isPresent()) {
                        increaseAdaptiveDelay(site, settings.adaptiveDelayMaxMillis(), settings.adaptiveDelayMaxMillis());
                        openCircuit(site, Duration.ofSeconds(settings.accessDeniedCooldownSeconds()),
                                "检测到疑似反爬验证页：" + softBlock.get(), timed.pageUrl().toString());
                        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                                "检测到疑似反爬验证页（" + softBlock.get() + "），已暂停该站点请求");
                    }
                    recordSuccess(site);
                    return new FetchResult(html, response.statusCode(), timed.durationMillis(),
                            response.headers().firstValue("ETag").orElse(null),
                            response.headers().firstValue("Last-Modified").orElse(null));
                }
                if (Set.of(401, 403, 451).contains(response.statusCode())) {
                    increaseAdaptiveDelay(site, settings.adaptiveDelayMaxMillis(), settings.adaptiveDelayMaxMillis());
                    openCircuit(site, Duration.ofSeconds(settings.accessDeniedCooldownSeconds()),
                            "源站拒绝访问（HTTP " + response.statusCode() + "）", timed.pageUrl().toString());
                    throw new ResponseStatusException(HttpStatusCode.valueOf(response.statusCode()),
                            "源站拒绝访问（HTTP " + response.statusCode() + "），已暂停该站点请求且不会切换代理重试");
                }
                if (response.statusCode() != 429 && response.statusCode() < 500) {
                    recordFailure(site, settings.maxConsecutiveFailures(), settings.circuitCooldownSeconds());
                    throw new ResponseStatusException(HttpStatusCode.valueOf(response.statusCode()),
                            "源站返回 HTTP " + response.statusCode() + "，该响应不会自动重试");
                }
                last = new IllegalStateException("源站返回 HTTP " + response.statusCode());
                if (response.statusCode() == 429) {
                    OptionalLong requestedDelay = retryAfterMillis(response.headers(), Instant.now());
                    retryDelay = requestedDelay.orElse(retryDelay);
                    openCircuitUntil(site, Instant.now().plusMillis(Math.max(1000L, retryDelay)),
                            "源站要求降低请求频率（HTTP 429）", timed.pageUrl().toString());
                    if (retryDelay > settings.maxInlineRetryDelayMillis()) break;
                }
                increaseAdaptiveDelay(site, retryDelay, settings.adaptiveDelayMaxMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw exception;
            } catch (ResponseStatusException exception) {
                throw exception;
            } catch (IOException exception) {
                last = exception;
                if (proxies.size() > 1) proxyIndex++;
            } catch (Exception exception) {
                last = exception;
            }
            if (attempt + 1 < attempts) Thread.sleep(retryDelay);
        }
        if (last instanceof IOException) {
            increaseAdaptiveDelay(site, retryBackoffMillis(Math.max(0, attempts - 1),
                    settings.retryBackoffMaxMillis()), settings.adaptiveDelayMaxMillis());
        }
        recordFailure(site, settings.maxConsecutiveFailures(), settings.circuitCooldownSeconds());
        throw last == null ? new IllegalStateException("请求失败") : last;
    }

    private TimedResponse sendFollowingSafeRedirects(CrawlerSite site, URI original, String etag,
            String lastModified, String proxyUrl, CrawlerRequestSettings settings) throws Exception {
        AdjustableConcurrencyGate gate = concurrencyGates.computeIfAbsent(site.getId(),
                ignored -> new AdjustableConcurrencyGate());
        gate.acquire(Math.max(1, value(site.getMaxConcurrency(), 1)), site.getUpdatedAt());
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
        for (int redirects = 0; redirects <= settings.maxRedirects(); redirects++) {
            String origin = originKey(current);
            AdjustableConcurrencyGate originGate = originConcurrencyGates.computeIfAbsent(
                    origin, ignored -> new AdjustableConcurrencyGate());
            originGate.acquire(settings.maxOriginConcurrency(), null);
            NetworkResponse response;
            long started = System.nanoTime();
            try {
                throttle(site, current);
                HttpRequest.Builder request = HttpRequest.newBuilder(current)
                        .timeout(Duration.ofMillis(settings.timeoutMillis()))
                        .GET().header("Accept", "text/html,application/xhtml+xml")
                        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6")
                        .header("User-Agent", defaultString(settings.userAgent(), DEFAULT_USER_AGENT));
                if (etag != null && !etag.isBlank()) request.header("If-None-Match", etag);
                if (lastModified != null && !lastModified.isBlank()) request.header("If-Modified-Since", lastModified);
                if (!settings.cookie().isBlank()) request.header("Cookie", settings.cookie());
                applyHeaders(request, settings.headersJson());
                HttpResponse<InputStream> rawResponse = client(settings.timeoutMillis(), proxyUrl)
                        .send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
                byte[] responseBody;
                try (InputStream input = rawResponse.body()) {
                    responseBody = readBounded(input, contentLength(rawResponse.headers()),
                            settings.maxResponseSizeMb() * 1024 * 1024);
                }
                response = new NetworkResponse(
                        rawResponse.statusCode(), rawResponse.headers(), responseBody);
            } finally {
                originGate.release();
            }
            duration += (System.nanoTime() - started) / 1_000_000;
            if (!Set.of(301, 302, 303, 307, 308).contains(response.statusCode())) {
                return new TimedResponse(response, duration, current);
            }
            String location = response.headers().firstValue("Location")
                    .orElseThrow(() -> new IllegalStateException("源站重定向缺少 Location"));
            URI redirected = validateSiteUrl(site, current.resolve(location).toString());
            if (!Boolean.FALSE.equals(site.getRespectRobotsTxt())
                    && !originKey(current).equals(originKey(redirected))) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "源站发生跨源重定向，无法沿用当前 robots.txt 策略；请将网站基础地址改为最终地址");
            }
            current = redirected;
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
        HttpClientKey key = new HttpClientKey(timeoutMillis, defaultString(proxyUrl, ""));
        return clients.computeIfAbsent(key, ignored -> {
            HttpClient.Builder builder = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                    .connectTimeout(Duration.ofMillis(timeoutMillis));
            if (proxyUrl != null && !proxyUrl.isBlank()) {
                URI proxy = URI.create(proxyUrl.contains("://") ? proxyUrl : "http://" + proxyUrl);
                builder.proxy(ProxySelector.of(new InetSocketAddress(proxy.getHost(), proxy.getPort())));
            }
            return builder.build();
        });
    }

    List<String> proxyUrls(CrawlerSite site) {
        if (site.getProxy() != null && !site.getProxy().isBlank()) return List.of(site.getProxy());
        return proxySettingsService.activeCrawlerProxyUrls();
    }

    int maxConsecutiveFailures() { return requestSettings().maxConsecutiveFailures(); }

    int cachedHttpClientCount() { return clients.size(); }

    public void refreshGlobalConfiguration(CrawlerRequestSettings settings) {
        robotsCache.clear();
        robotsLocks.clear();
        adaptiveDelays.values().forEach(delay -> delay.cap(settings.adaptiveDelayMaxMillis()));
    }

    void refreshSiteConfiguration(CrawlerSite site) {
        if (site.getId() == null) return;
        concurrencyGates.computeIfAbsent(site.getId(), ignored -> new AdjustableConcurrencyGate())
                .updateLimit(Math.max(1, value(site.getMaxConcurrency(), 1)), LocalDateTime.now());
        String keyPrefix = site.getId() + "|";
        robotsCache.keySet().removeIf(key -> key.startsWith(keyPrefix));
        robotsLocks.keySet().removeIf(key -> key.startsWith(keyPrefix));
    }

    void removeSiteRuntimeState(Long siteId) {
        if (siteId == null) return;
        concurrencyGates.remove(siteId);
        circuitStates.remove(siteId);
        adaptiveDelays.remove(siteId);
        siteNextRequests.remove(siteId);
        String keyPrefix = siteId + "|";
        robotsCache.keySet().removeIf(key -> key.startsWith(keyPrefix));
        robotsLocks.keySet().removeIf(key -> key.startsWith(keyPrefix));
    }

    ProtectionState protectionState(CrawlerSite site) {
        CircuitState state = circuitStates.get(site.getId());
        Instant persistedUntil = site.getCrawlerBlockedUntil();
        Instant blockedUntil = later(state == null ? null : state.blockedUntil(), persistedUntil);
        String reason = state != null && state.reason() != null
                ? state.reason() : site.getCrawlerBlockReason();
        AdaptiveDelay adaptive = adaptiveDelays.get(site.getId());
        String pageUrl = state != null && state.pageUrl() != null
                ? state.pageUrl() : site.getCrawlerBlockUrl();
        return new ProtectionState(blockedUntil != null && blockedUntil.isAfter(Instant.now()),
                blockedUntil, reason, pageUrl, state == null ? 0 : state.failures(),
                adaptive == null ? 0 : adaptive.current());
    }

    void resetProtection(CrawlerSite site) {
        circuitStates.remove(site.getId());
        adaptiveDelays.remove(site.getId());
        siteNextRequests.remove(site.getId());
        persistProtection(site, null, null, null);
    }

    private void enforceRobots(CrawlerSite site, URI target, CrawlerRequestSettings settings) throws Exception {
        if (Boolean.FALSE.equals(site.getRespectRobotsTxt())) return;
        if ("/robots.txt".equals(target.getPath())) return;
        String key = robotsKey(site, target);
        Instant now = Instant.now();
        RobotsCacheEntry cached = robotsCache.get(key);
        if (cached == null || !cached.expiresAt().isAfter(now)) {
            Object lock = robotsLocks.computeIfAbsent(key, ignored -> new Object());
            synchronized (lock) {
                cached = robotsCache.get(key);
                if (cached == null || !cached.expiresAt().isAfter(now)) {
                    cached = fetchRobotsPolicy(site, target, settings, now);
                    robotsCache.put(key, cached);
                }
            }
        }
        if (!cached.policy().allows(target)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "robots.txt 不允许采集该地址，系统不会发起目标请求");
        }
    }

    private RobotsCacheEntry fetchRobotsPolicy(
            CrawlerSite site, URI target, CrawlerRequestSettings settings, Instant now) throws Exception {
        URI robotsUri = new URI(target.getScheme(), null, target.getHost(), target.getPort(),
                "/robots.txt", null, null);
        List<String> proxies = proxyUrls(site);
        String proxyUrl = proxies.isEmpty() ? null : proxies.getFirst();
        try {
            TimedResponse timed = sendFollowingSafeRedirects(site, robotsUri, null, null, proxyUrl, settings);
            int status = timed.response().statusCode();
            if (status >= 200 && status < 300) {
                Charset charset = Charset.forName(defaultString(site.getEncoding(), "UTF-8"));
                String body = new String(timed.response().body(), charset);
                return new RobotsCacheEntry(CrawlerRobotsPolicy.parse(body, productToken(settings.userAgent())),
                        now.plus(Duration.ofMinutes(settings.robotsCacheMinutes())));
            }
            if (status == 404 || status == 410) {
                return new RobotsCacheEntry(CrawlerRobotsPolicy.ALLOW_ALL,
                        now.plus(Duration.ofMinutes(settings.robotsCacheMinutes())));
            }
            if (status == 401 || status == 403) {
                return new RobotsCacheEntry(CrawlerRobotsPolicy.DISALLOW_ALL,
                        now.plus(Duration.ofMinutes(settings.robotsErrorCacheMinutes())));
            }
            if (status == 429 || status == 503) {
                long delay = retryAfterMillis(timed.response().headers(), now)
                        .orElse(Duration.ofMinutes(settings.robotsErrorCacheMinutes()).toMillis());
                Instant blockedUntil = now.plusMillis(Math.max(1000L, delay));
                robotsCache.put(robotsKey(site, target),
                        new RobotsCacheEntry(CrawlerRobotsPolicy.DISALLOW_ALL, blockedUntil));
                openCircuitUntil(site, blockedUntil, "robots.txt 要求稍后重试",
                        timed.pageUrl().toString());
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "robots.txt 要求稍后重试，站点请求已进入冷却期");
            }
            throw new IllegalStateException("robots.txt 返回 HTTP " + status);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            robotsCache.put(robotsKey(site, target), new RobotsCacheEntry(
                    CrawlerRobotsPolicy.DISALLOW_ALL,
                    now.plus(Duration.ofMinutes(settings.robotsErrorCacheMinutes()))));
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "暂时无法确认 robots.txt 访问策略，已暂停目标请求", exception);
        }
    }

    private String productToken(String configuredUserAgent) {
        String userAgent = defaultString(configuredUserAgent, DEFAULT_USER_AGENT).trim();
        int separator = userAgent.indexOf('/');
        int whitespace = userAgent.indexOf(' ');
        int end = separator > 0 ? separator : whitespace > 0 ? whitespace : userAgent.length();
        return userAgent.substring(0, end);
    }

    static OptionalLong retryAfterMillis(HttpHeaders headers, Instant now) {
        Optional<String> value = headers.firstValue("Retry-After");
        if (value.isEmpty() || value.get().isBlank()) return OptionalLong.empty();
        String text = value.get().trim();
        try {
            long seconds = Long.parseLong(text);
            return OptionalLong.of(Math.max(0, Math.multiplyExact(seconds, 1000L)));
        } catch (NumberFormatException | ArithmeticException ignored) {
            try {
                Instant retryAt = ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                return OptionalLong.of(Math.max(0, Duration.between(now, retryAt).toMillis()));
            } catch (DateTimeParseException invalidDate) {
                return OptionalLong.empty();
            }
        }
    }

    static byte[] readBounded(InputStream input, long declaredLength, int maximumBytes) throws IOException {
        if (declaredLength > maximumBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "源站响应超过允许的 " + maximumBytes / (1024 * 1024) + " MiB 上限");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(
                (int) Math.min(Math.max(0, declaredLength), 64 * 1024));
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maximumBytes) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                        "源站响应超过允许的 " + maximumBytes / (1024 * 1024) + " MiB 上限");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    static Optional<String> detectSoftBlock(String html) {
        if (html == null || html.isBlank() || html.length() > 1024 * 1024) return Optional.empty();
        String lower = html.toLowerCase(Locale.ROOT);
        if (CHALLENGE_TITLE.matcher(html).find()) return Optional.of("验证或拒绝访问页面");
        boolean challengeProvider = lower.contains("/cdn-cgi/challenge-platform")
                || lower.contains("cf-chl-") || lower.contains("g-recaptcha")
                || lower.contains("h-captcha") || lower.contains("cf-turnstile")
                || lower.contains("geetest_");
        boolean challengePrompt = lower.contains("verify you are human")
                || lower.contains("checking your browser") || html.contains("请完成安全验证")
                || html.contains("请先完成验证") || html.contains("访问过于频繁")
                || html.contains("请求过于频繁");
        return challengeProvider && challengePrompt ? Optional.of("人机验证页面") : Optional.empty();
    }

    private void validateContentType(NetworkResponse response) {
        String contentType = response.headers().firstValue("Content-Type").orElse("")
                .toLowerCase(Locale.ROOT);
        if (!contentType.isBlank() && !contentType.startsWith("text/html")
                && !contentType.startsWith("application/xhtml+xml")
                && !contentType.startsWith("text/plain")) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "源站返回了不支持的内容类型：" + contentType);
        }
    }

    private long contentLength(HttpHeaders headers) {
        try {
            return headers.firstValueAsLong("Content-Length").orElse(-1L);
        } catch (NumberFormatException exception) {
            return -1L;
        }
    }

    private long retryBackoffMillis(int attempt, int maximumMillis) {
        long ceiling = Math.min(maximumMillis, 1000L << Math.min(attempt, 16));
        return ThreadLocalRandom.current().nextLong(Math.max(1L, ceiling / 2), ceiling + 1);
    }

    private void ensureCircuitClosed(CrawlerSite site) {
        CircuitState state = circuitStates.get(site.getId());
        if (site.getCrawlerBlockedUntil() != null
                && (state == null || state.blockedUntil() == null
                || site.getCrawlerBlockedUntil().isAfter(state.blockedUntil()))) {
            state = new CircuitState(state == null ? 0 : state.failures(),
                    site.getCrawlerBlockedUntil(), site.getCrawlerBlockReason(),
                    site.getCrawlerBlockUrl());
            circuitStates.put(site.getId(), state);
        }
        if (state == null) return;
        Instant blockedUntil = state.blockedUntil();
        if (blockedUntil != null && blockedUntil.isAfter(Instant.now())) {
            long seconds = Math.max(1, Duration.between(Instant.now(), blockedUntil).toSeconds());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "站点请求处于冷却期，请约 " + seconds + " 秒后重试");
        }
        if (blockedUntil != null) {
            circuitStates.remove(site.getId(), state);
            persistProtection(site, null, null, null);
        }
    }

    private void recordSuccess(CrawlerSite site) {
        circuitStates.remove(site.getId());
        AdaptiveDelay delay = adaptiveDelays.get(site.getId());
        if (delay != null && delay.recover() == 0) adaptiveDelays.remove(site.getId(), delay);
    }

    private void recordFailure(CrawlerSite site, int maximumFailures, int cooldownSeconds) {
        circuitStates.compute(site.getId(), (ignored, current) -> {
            int failures = current == null ? 1 : current.failures() + 1;
            Instant blockedUntil = failures >= Math.max(1, maximumFailures)
                    ? Instant.now().plusSeconds(cooldownSeconds)
                    : current == null ? null : current.blockedUntil();
            return new CircuitState(failures, blockedUntil,
                    blockedUntil == null ? current == null ? null : current.reason()
                            : "连续请求失败达到保护阈值",
                    current == null ? null : current.pageUrl());
        });
        CircuitState state = circuitStates.get(site.getId());
        if (state != null && state.blockedUntil() != null) {
            persistProtection(site, state.blockedUntil(), state.reason(), state.pageUrl());
        }
    }

    private void openCircuit(CrawlerSite site, Duration duration, String reason, String pageUrl) {
        openCircuitUntil(site, Instant.now().plus(duration), reason, pageUrl);
    }

    private void openCircuitUntil(CrawlerSite site, Instant blockedUntil, String reason, String pageUrl) {
        CircuitState state = circuitStates.compute(site.getId(), (ignored, current) -> new CircuitState(
                current == null ? 1 : current.failures() + 1,
                current == null || current.blockedUntil() == null
                        || blockedUntil.isAfter(current.blockedUntil()) ? blockedUntil : current.blockedUntil(),
                reason == null ? current == null ? null : current.reason() : reason,
                pageUrl == null ? current == null ? null : current.pageUrl() : pageUrl));
        persistProtection(site, state.blockedUntil(), state.reason(), state.pageUrl());
    }

    private void persistProtection(CrawlerSite site, Instant blockedUntil, String reason, String pageUrl) {
        site.setCrawlerBlockedUntil(blockedUntil);
        site.setCrawlerBlockReason(reason);
        site.setCrawlerBlockUrl(pageUrl);
        if (site.getId() != null) {
            crawlerSiteRepository.updateCrawlerProtection(site.getId(), blockedUntil, reason);
            crawlerSiteRepository.updateCrawlerProtectionUrl(site.getId(), pageUrl);
        }
    }

    private Instant later(Instant first, Instant second) {
        if (first == null) return second;
        if (second == null) return first;
        return first.isAfter(second) ? first : second;
    }

    private void increaseAdaptiveDelay(CrawlerSite site, long minimumDelayMillis, long maximumDelayMillis) {
        adaptiveDelays.computeIfAbsent(site.getId(), ignored -> new AdaptiveDelay())
                .increase(minimumDelayMillis, maximumDelayMillis);
    }

    private String robotsKey(CrawlerSite site, URI target) {
        return site.getId() + "|" + target.getScheme() + "://" + target.getAuthority();
    }

    private CrawlerRequestSettings requestSettings() {
        CrawlerRequestSettings settings = crawlerSettingsService.settings();
        return settings == null ? new CrawlerRequestSettings(15000, 2, 5, 30000, 30000,
                8, 5, 4, 60000, 900, 3600, 360, 15, true, "", "", "{}") : settings;
    }

    private void throttle(CrawlerSite site, URI target) throws InterruptedException {
        long now = System.currentTimeMillis();
        AdaptiveDelay adaptiveDelay = adaptiveDelays.get(site.getId());
        long interval = value(site.getRequestIntervalMillis(), 1500) +
                (value(site.getRandomDelayMillis(), 1000) == 0 ? 0
                        : ThreadLocalRandom.current().nextInt(value(site.getRandomDelayMillis(), 1000) + 1))
                + (adaptiveDelay == null ? 0 : adaptiveDelay.current());
        long siteSlot = reserveRequestSlot(
                siteNextRequests.computeIfAbsent(site.getId(), ignored -> new AtomicLong()), now, interval);
        long originSlot = reserveRequestSlot(
                originNextRequests.computeIfAbsent(originKey(target), ignored -> new AtomicLong()), now, interval);
        long wait = Math.max(siteSlot, originSlot) - now;
        if (wait > 0) Thread.sleep(wait);
    }

    static long reserveRequestSlot(AtomicLong gate, long now, long interval) {
        long slot;
        do {
            slot = gate.get();
        } while (!gate.compareAndSet(slot, Math.max(now, slot) + Math.max(0, interval)));
        return Math.max(now, slot);
    }

    static String originKey(URI uri) {
        String scheme = Objects.toString(uri.getScheme(), "").toLowerCase(Locale.ROOT);
        String host = Objects.toString(uri.getHost(), "").toLowerCase(Locale.ROOT);
        int port = uri.getPort() >= 0 ? uri.getPort() : "https".equals(scheme) ? 443 : 80;
        return scheme + "://" + host + ":" + port;
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
    private record TimedResponse(NetworkResponse response, long durationMillis, URI pageUrl) { }
    private record NetworkResponse(int statusCode, HttpHeaders headers, byte[] body) { }
    private record RobotsCacheEntry(CrawlerRobotsPolicy policy, Instant expiresAt) { }
    private record CircuitState(int failures, Instant blockedUntil, String reason, String pageUrl) { }
    private record HttpClientKey(int timeoutMillis, String proxyUrl) { }
    record ProtectionState(boolean coolingDown, Instant blockedUntil, String reason, String pageUrl,
            int consecutiveFailures, long adaptiveDelayMillis) {
        ProtectionState(boolean coolingDown, Instant blockedUntil, String reason,
                int consecutiveFailures, long adaptiveDelayMillis) {
            this(coolingDown, blockedUntil, reason, null, consecutiveFailures, adaptiveDelayMillis);
        }
    }

    static final class AdaptiveDelay {
        private long delayMillis;

        synchronized long increase(long minimumDelayMillis, long maximumDelayMillis) {
            delayMillis = Math.min(maximumDelayMillis,
                    Math.max(Math.max(1000L, minimumDelayMillis), delayMillis == 0 ? 1000L : delayMillis * 2));
            return delayMillis;
        }

        synchronized long recover() {
            delayMillis = delayMillis <= 1000L ? 0 : delayMillis / 2;
            return delayMillis;
        }

        synchronized long current() {
            return delayMillis;
        }

        synchronized void cap(long maximumDelayMillis) {
            delayMillis = Math.min(delayMillis, maximumDelayMillis);
        }
    }

    static final class AdjustableConcurrencyGate {
        private int active;
        private int limit = 1;
        private LocalDateTime configurationUpdatedAt;

        synchronized void acquire(int requestedLimit, LocalDateTime updatedAt) throws InterruptedException {
            updateLimit(requestedLimit, updatedAt);
            while (active >= limit) wait();
            active++;
        }

        synchronized void updateLimit(int requestedLimit, LocalDateTime updatedAt) {
            if (configurationUpdatedAt == null
                    || (updatedAt != null && !updatedAt.isBefore(configurationUpdatedAt))) {
                limit = Math.max(1, requestedLimit);
                configurationUpdatedAt = updatedAt;
                notifyAll();
            }
        }

        synchronized int limit() {
            return limit;
        }

        synchronized void release() {
            active--;
            notifyAll();
        }
    }
}
