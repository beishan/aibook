package com.aibook.service.crawler;

import com.aibook.dto.CrawlerSettingsDtos.CrawlerRequestSettings;
import com.aibook.model.entity.CrawlerSite;
import com.aibook.service.CrawlerSettingsService;
import com.aibook.service.ProxySettingsService;
import com.aibook.repository.CrawlerSiteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpHeaders;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CrawlerHttpClientTest {
    private final ProxySettingsService proxySettings = mock(ProxySettingsService.class);
    private final CrawlerSettingsService crawlerSettings = mock(CrawlerSettingsService.class);
    private final CrawlerSiteRepository siteRepository = mock(CrawlerSiteRepository.class);
    private final CrawlerHttpClient client = new CrawlerHttpClient(
            new ObjectMapper(), proxySettings, crawlerSettings, siteRepository);
    private final CrawlerSite site = CrawlerSite.builder().id(1L).baseUrl("https://novel.example.com").build();

    @Test void acceptsSameDomainAndSubdomain() {
        assertEquals("novel.example.com", client.validateSiteUrl(site, "https://novel.example.com/book/1").getHost());
        assertEquals("img.novel.example.com", client.validateSiteUrl(site, "https://img.novel.example.com/cover/1").getHost());
    }

    @Test void rejectsForeignDomainAndNonHttpScheme() {
        assertThrows(ResponseStatusException.class, () -> client.validateSiteUrl(site, "https://evil.example/book/1"));
        assertThrows(ResponseStatusException.class, () -> client.validateSiteUrl(site, "file:///etc/passwd"));
    }

    @Test void siteProxyOverridesCrawlerPoolOtherwiseUsesPriorityOrderedPool() {
        when(proxySettings.activeCrawlerProxyUrls()).thenReturn(java.util.List.of(
                "http://proxy-high:8080", "http://proxy-low:8080"));
        assertEquals(java.util.List.of("http://proxy-high:8080", "http://proxy-low:8080"),
                client.proxyUrls(site));

        site.setProxy("http://site-proxy:7890");
        assertEquals(java.util.List.of("http://site-proxy:7890"), client.proxyUrls(site));
    }

    @Test void usesGlobalConsecutiveFailureLimit() {
        when(crawlerSettings.settings()).thenReturn(
                new CrawlerRequestSettings(30000, 4, 7, "CrawlerBot/2.0", "", "{}"));

        assertEquals(7, client.maxConsecutiveFailures());
    }

    @Test void parsesRetryAfterSecondsAndHttpDate() {
        Instant now = Instant.parse("2026-09-16T00:00:00Z");
        HttpHeaders seconds = HttpHeaders.of(Map.of("Retry-After", List.of("12")), (a, b) -> true);
        String dateValue = DateTimeFormatter.RFC_1123_DATE_TIME.format(now.plusSeconds(30).atZone(ZoneOffset.UTC));
        HttpHeaders date = HttpHeaders.of(Map.of("Retry-After", List.of(dateValue)), (a, b) -> true);

        assertEquals(12_000L, CrawlerHttpClient.retryAfterMillis(seconds, now).orElseThrow());
        assertEquals(30_000L, CrawlerHttpClient.retryAfterMillis(date, now).orElseThrow());
    }

    @Test void boundsStreamingResponsesAndDetectsSoftBlockingPages() throws Exception {
        assertThrows(ResponseStatusException.class, () -> CrawlerHttpClient.readBounded(
                new ByteArrayInputStream(new byte[0]), 8L * 1024 * 1024 + 1));
        assertThrows(ResponseStatusException.class, () -> CrawlerHttpClient.readBounded(
                new ByteArrayInputStream(new byte[8 * 1024 * 1024 + 1]), -1));

        assertTrue(CrawlerHttpClient.detectSoftBlock("""
                <html><head><title>Just a moment...</title></head>
                <body><script src="/cdn-cgi/challenge-platform/test"></script>Checking your browser</body></html>
                """).isPresent());
        assertTrue(CrawlerHttpClient.detectSoftBlock("<title>访问过于频繁</title>").isPresent());
        assertTrue(CrawlerHttpClient.detectSoftBlock(
                "<article>主人公在输入验证码之后继续阅读。</article>").isEmpty());
    }

    @Test void adaptiveDelayIncreasesOnFailuresAndRecoversGradually() {
        CrawlerHttpClient.AdaptiveDelay delay = new CrawlerHttpClient.AdaptiveDelay();

        assertEquals(1000, delay.increase(500));
        assertEquals(2000, delay.increase(500));
        assertEquals(1000, delay.recover());
        assertEquals(0, delay.recover());
    }

    @Test void appliesNewerConcurrencyLimitWithoutOldConfigurationOverwritingIt() throws Exception {
        CrawlerHttpClient.AdjustableConcurrencyGate gate =
                new CrawlerHttpClient.AdjustableConcurrencyGate();
        LocalDateTime oldConfiguration = LocalDateTime.parse("2026-09-16T08:00:00");
        LocalDateTime newConfiguration = oldConfiguration.plusMinutes(1);
        gate.acquire(2, oldConfiguration);
        gate.acquire(2, oldConfiguration);

        var executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        Future<?> waiting = executor.submit(() -> {
            started.countDown();
            gate.acquire(1, newConfiguration);
            gate.release();
            return null;
        });
        assertTrue(started.await(1, TimeUnit.SECONDS));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (gate.limit() != 1 && System.nanoTime() < deadline) Thread.onSpinWait();
        assertEquals(1, gate.limit());
        gate.release();
        assertFalse(waiting.isDone());
        gate.updateLimit(3, oldConfiguration);
        assertEquals(1, gate.limit());
        gate.release();
        waiting.get(1, TimeUnit.SECONDS);
        executor.shutdownNow();
    }

    @Test void doesNotRetryForbiddenResponseOrSwitchProxy() throws Exception {
        AtomicInteger targetRequests = new AtomicInteger();
        HttpServer server = server("User-agent: *\nAllow: /\n", exchange -> {
            targetRequests.incrementAndGet();
            respond(exchange, 403, "Forbidden");
        });
        try {
            CrawlerHttpClient httpClient = configuredClient(4);
            CrawlerSite localSite = localSite(server, 11L);

            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> httpClient.get(localSite, localSite.getBaseUrl() + "/chapter/1"));

            assertEquals(403, exception.getStatusCode().value());
            assertEquals(1, targetRequests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test void blocksTargetRequestDisallowedByRobotsTxt() throws Exception {
        AtomicInteger targetRequests = new AtomicInteger();
        HttpServer server = server("User-agent: AiBookCrawler\nDisallow: /private/\n", exchange -> {
            targetRequests.incrementAndGet();
            respond(exchange, 200, "secret");
        });
        try {
            CrawlerHttpClient httpClient = configuredClient(0);
            CrawlerSite localSite = localSite(server, 12L);

            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> httpClient.get(localSite, localSite.getBaseUrl() + "/private/1"));

            assertEquals(403, exception.getStatusCode().value());
            assertEquals(0, targetRequests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test void skipsRobotsTxtWhenDisabledForSite() throws Exception {
        AtomicInteger targetRequests = new AtomicInteger();
        HttpServer server = server("User-agent: *\nDisallow: /\n", exchange -> {
            targetRequests.incrementAndGet();
            respond(exchange, 200, "allowed by site configuration");
        });
        try {
            CrawlerHttpClient httpClient = configuredClient(0);
            CrawlerSite localSite = localSite(server, 15L);
            localSite.setRespectRobotsTxt(false);

            CrawlerHttpClient.FetchResult result = httpClient.get(
                    localSite, localSite.getBaseUrl() + "/private/1");

            assertEquals(200, result.statusCode());
            assertEquals(1, targetRequests.get());
            assertEquals(1, httpClient.cachedHttpClientCount());
        } finally {
            server.stop(0);
        }
    }

    @Test void rejectsUnexpectedSuccessfulContentTypeWithoutRetrying() throws Exception {
        AtomicInteger targetRequests = new AtomicInteger();
        HttpServer server = server("User-agent: *\nAllow: /\n", exchange -> {
            targetRequests.incrementAndGet();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            respond(exchange, 200, "{}");
        });
        try {
            CrawlerHttpClient httpClient = configuredClient(3);
            CrawlerSite localSite = localSite(server, 16L);

            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> httpClient.get(localSite, localSite.getBaseUrl() + "/chapter/1"));

            assertEquals(415, exception.getStatusCode().value());
            assertEquals(1, targetRequests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test void retriesRateLimitUsingRetryAfterAndAcceptsNotModifiedWithoutLocation() throws Exception {
        AtomicInteger targetRequests = new AtomicInteger();
        HttpServer server = server("User-agent: *\nAllow: /\n", exchange -> {
            int request = targetRequests.incrementAndGet();
            if (request == 1) {
                exchange.getResponseHeaders().add("Retry-After", "0");
                respond(exchange, 429, "slow down");
            } else {
                respondWithoutBody(exchange, 304);
            }
        });
        try {
            CrawlerHttpClient httpClient = configuredClient(1);
            CrawlerSite localSite = localSite(server, 13L);

            CrawlerHttpClient.FetchResult result = httpClient.get(
                    localSite, localSite.getBaseUrl() + "/chapter/1", "etag", null);

            assertEquals(304, result.statusCode());
            assertEquals(2, targetRequests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test void opensSiteCircuitAfterConfiguredConsecutiveFailures() throws Exception {
        AtomicInteger targetRequests = new AtomicInteger();
        HttpServer server = server("User-agent: *\nAllow: /\n", exchange -> {
            targetRequests.incrementAndGet();
            respond(exchange, 503, "unavailable");
        });
        try {
            CrawlerHttpClient httpClient = configuredClient(0, 2);
            CrawlerSite localSite = localSite(server, 14L);
            String url = localSite.getBaseUrl() + "/chapter/1";

            assertThrows(IllegalStateException.class, () -> httpClient.get(localSite, url));
            assertThrows(IllegalStateException.class, () -> httpClient.get(localSite, url));
            ResponseStatusException circuitOpen = assertThrows(
                    ResponseStatusException.class, () -> httpClient.get(localSite, url));

            assertEquals(503, circuitOpen.getStatusCode().value());
            assertEquals(2, targetRequests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test void persistsSignificantCooldownAndManualResetClearsAllProtectionState() throws Exception {
        AtomicInteger targetRequests = new AtomicInteger();
        HttpServer server = server("User-agent: *\nAllow: /\n", exchange -> {
            targetRequests.incrementAndGet();
            respond(exchange, 403, "Forbidden");
        });
        CrawlerSiteRepository repository = mock(CrawlerSiteRepository.class);
        try {
            CrawlerHttpClient httpClient = configuredClient(0, 3, repository);
            CrawlerSite localSite = localSite(server, 17L);

            assertThrows(ResponseStatusException.class,
                    () -> httpClient.get(localSite, localSite.getBaseUrl() + "/chapter/1"));
            CrawlerHttpClient.ProtectionState protectedState = httpClient.protectionState(localSite);

            assertTrue(protectedState.coolingDown());
            assertTrue(protectedState.reason().contains("HTTP 403"));
            verify(repository).updateCrawlerProtection(
                    eq(17L), eq(localSite.getCrawlerBlockedUntil()), contains("HTTP 403"));

            httpClient.resetProtection(localSite);

            assertFalse(httpClient.protectionState(localSite).coolingDown());
            assertNull(localSite.getCrawlerBlockedUntil());
            verify(repository).updateCrawlerProtection(17L, null, null);
        } finally {
            server.stop(0);
        }
    }

    @Test void restoresPersistedCooldownBeforeSendingAnyRequest() throws Exception {
        AtomicInteger targetRequests = new AtomicInteger();
        HttpServer server = server("User-agent: *\nAllow: /\n", exchange -> {
            targetRequests.incrementAndGet();
            respond(exchange, 200, "content");
        });
        try {
            CrawlerHttpClient httpClient = configuredClient(0);
            CrawlerSite localSite = localSite(server, 18L);
            localSite.setCrawlerBlockedUntil(Instant.now().plusSeconds(60));
            localSite.setCrawlerBlockReason("上次运行触发源站限流");

            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> httpClient.get(localSite, localSite.getBaseUrl() + "/chapter/1"));

            assertEquals(503, exception.getStatusCode().value());
            assertEquals(0, targetRequests.get());
        } finally {
            server.stop(0);
        }
    }

    private CrawlerHttpClient configuredClient(int retries) {
        return configuredClient(retries, 3);
    }

    private CrawlerHttpClient configuredClient(int retries, int maximumFailures) {
        return configuredClient(retries, maximumFailures, mock(CrawlerSiteRepository.class));
    }

    private CrawlerHttpClient configuredClient(int retries, int maximumFailures,
            CrawlerSiteRepository repository) {
        ProxySettingsService proxies = mock(ProxySettingsService.class);
        CrawlerSettingsService settings = mock(CrawlerSettingsService.class);
        when(proxies.activeCrawlerProxyUrls()).thenReturn(List.of());
        when(settings.settings()).thenReturn(
                new CrawlerRequestSettings(5000, retries, maximumFailures,
                        "AiBookCrawler/1.0", "", "{}"));
        return new CrawlerHttpClient(new ObjectMapper(), proxies, settings, repository);
    }

    private CrawlerSite localSite(HttpServer server, long id) {
        return CrawlerSite.builder()
                .id(id)
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .requestIntervalMillis(0)
                .randomDelayMillis(0)
                .maxConcurrency(1)
                .encoding("UTF-8")
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private HttpServer server(String robots, ExchangeHandler targetHandler) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/robots.txt", exchange -> respond(exchange, 200, robots));
        server.createContext("/", exchange -> targetHandler.handle(exchange));
        server.start();
        return server;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void respondWithoutBody(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
