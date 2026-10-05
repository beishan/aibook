package com.aibook.service.crawler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aibook.dto.crawler.MihomoDtos.PolicyPayload;
import com.aibook.model.entity.*;
import com.aibook.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.util.ReflectionTestUtils;
import com.aibook.service.CrawlerSettingsService;
import com.aibook.service.ProxySettingsService;
import com.sun.net.httpserver.HttpServer;
import java.nio.charset.StandardCharsets;

class CrawlerMihomoServiceTest {
    private final CrawlerMihomoPolicyRepository policies = mock(CrawlerMihomoPolicyRepository.class);
    private final CrawlerMihomoReceiptRepository receipts = mock(CrawlerMihomoReceiptRepository.class);
    private final CrawlerQueueExecutorRepository executors = mock(CrawlerQueueExecutorRepository.class);
    private final CrawlerSiteRepository sites = mock(CrawlerSiteRepository.class);
    private final MihomoApiClient api = mock(MihomoApiClient.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final CrawlerMihomoService service = new CrawlerMihomoService(
            policies, receipts, executors, sites, mapper, api, transactions);
    private final User user = User.builder().id(1L).username("owner").build();
    private final CrawlerSite site = CrawlerSite.builder().id(3L).user(user).build();
    private final CrawlerTaskQueue queue = CrawlerTaskQueue.builder().id(2L).user(user).site(site).build();
    private final CrawlerQueueExecutor executor = CrawlerQueueExecutor.builder()
            .id(4L).queue(queue).proxyMode(CrawlerQueueExecutor.ProxyMode.MIHOMO).build();
    private final CrawlerMihomoPolicy config = new CrawlerMihomoPolicy();
    private String actual = "A";

    @BeforeEach
    void setUp() throws Exception {
        config.setExecutorId(4L);
        config.setControllerUrl("http://localhost:9111");
        config.setSecret("private-secret");
        config.setProxyUrl("http://localhost:7895");
        config.setGroupName("crawler");
        config.setNodesJson("[\"A\",\"B\"]");
        config.setCurrentNode("A");
        when(policies.findById(4L)).thenReturn(Optional.of(config));
        when(policies.existsById(4L)).thenReturn(true);
        when(executors.findById(4L)).thenReturn(Optional.of(executor));
        when(api.proxies(anyString(), any())).thenAnswer(call -> mapper.readTree(
                "{\"proxies\":{\"crawler\":{\"type\":\"Selector\",\"now\":\"" + actual
                        + "\",\"all\":[\"A\",\"B\",\"DIRECT\"]},\"A\":{\"type\":\"SS\"},"
                        + "\"B\":{\"type\":\"SS\"},\"DIRECT\":{\"type\":\"Direct\"}}}"));
        when(api.delay(anyString(), any(), anyString())).thenReturn(10);
        doAnswer(call -> { actual = call.getArgument(3); return null; })
                .when(api).select(anyString(), any(), anyString(), anyString());
        doAnswer(call -> {
            Consumer<TransactionStatus> callback = call.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactions).executeWithoutResult(any());
    }

    @Test
    void switchesOnlyAfterThresholdAndHealthFailure() throws Exception {
        when(api.delay(anyString(), any(), eq("A"))).thenThrow(new MihomoApiClient.ApiException(503));
        AtomicInteger attempts = new AtomicInteger();
        String result = service.execute(4L, site, () -> {
            if (attempts.incrementAndGet() <= 2) throw new IOException("connect timeout");
            return "ok";
        });
        assertEquals("ok", result);
        assertEquals(3, attempts.get());
        assertEquals("B", config.getCurrentNode());
        assertTrue(config.getCooldownsJson().contains("A"));
        verify(api, times(1)).select(anyString(), any(), eq("crawler"), eq("B"));
    }

    @Test
    void healthyNodeWithTargetFailureWaitsWithoutSwitch() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        assertThrows(CrawlerHttpClient.NoAvailableQueueProxyException.class, () -> service.execute(4L, site, () -> {
            attempts.incrementAndGet();
            throw new IOException("target failed");
        }));
        assertEquals(2, attempts.get());
        assertNotNull(config.getRetryAt());
        verify(api, never()).select(anyString(), any(), anyString(), anyString());
    }

    @Test
    void controlAuthenticationFailureDoesNotMarkNodesDead() throws Exception {
        when(api.proxies(anyString(), any())).thenThrow(new MihomoApiClient.ApiException(401));
        assertThrows(CrawlerHttpClient.NoAvailableQueueProxyException.class,
                () -> service.execute(4L, site, () -> "unused"));
        assertEquals("{}", config.getCooldownsJson());
        verify(api, never()).delay(anyString(), any(), anyString());
    }

    @Test
    void websiteRestrictionDoesNotSwitchAndBecomesWaiting() throws Exception {
        assertThrows(CrawlerHttpClient.NoAvailableQueueProxyException.class, () -> service.execute(4L, site, () -> {
            throw new CrawlerHttpClient.NoAvailableQueueProxyException("website cooldown");
        }));
        assertEquals("website cooldown", config.getLastError());
        verify(api, never()).select(anyString(), any(), anyString(), anyString());
    }

    @Test
    void exhaustedPoolNeverFallsBackToDirect() throws Exception {
        when(api.delay(anyString(), any(), anyString())).thenThrow(new MihomoApiClient.ApiException(503));
        assertThrows(CrawlerHttpClient.NoAvailableQueueProxyException.class, () -> service.execute(4L, site, () -> {
            throw new IOException("failed");
        }));
        assertTrue(config.getCooldownsJson().contains("B"));
        config.setRetryAt(Instant.now().minusSeconds(1));
        AtomicInteger requests = new AtomicInteger();
        assertThrows(CrawlerHttpClient.NoAvailableQueueProxyException.class,
                () -> service.execute(4L, site, () -> requests.incrementAndGet()));
        assertEquals(0, requests.get());
        verify(api, never()).select(anyString(), any(), anyString(), anyString());
    }

    @Test
    void countersTriggerOneRotationAndResetTogether() throws Exception {
        config.setRotationChapters(2);
        config.setRotationTasks(1);
        config.setChapters(2);
        config.setTasks(1);
        service.execute(4L, site, () -> "ok");
        verify(api, times(1)).select(anyString(), any(), anyString(), eq("B"));
        assertEquals(0, config.getChapters());
        assertEquals(0, config.getTasks());
    }

    @Test
    void activeTimeRotatesButIdleAndRestartGapsAreNotCounted() throws Exception {
        config.setRotationSeconds(300);
        config.setActiveMillis(300000);
        config.setLastActivityAt(Instant.now().minusSeconds(86400));
        service.execute(4L, site, () -> "ok");
        verify(api, times(1)).select(anyString(), any(), anyString(), eq("B"));
        assertTrue(config.getActiveMillis() < 10000);
        assertNull(config.getLastActivityAt());
        service.execute(4L, site, () -> "ok");
        verify(api, times(1)).select(anyString(), any(), anyString(), anyString());
    }

    @Test
    void persistedReceiptsDeduplicateChapterAndTaskAccounting() {
        Set<String> keys = new HashSet<>();
        when(receipts.existsByExecutorIdAndEventKey(eq(4L), anyString()))
                .thenAnswer(call -> keys.contains(call.getArgument(1)));
        when(receipts.save(any())).thenAnswer(call -> {
            CrawlerMihomoReceipt receipt = call.getArgument(0);
            keys.add(receipt.getEventKey());
            return receipt;
        });
        service.account(4L, "chapter:task:1", true);
        service.account(4L, "chapter:task:1", true);
        service.account(4L, "task:task", false);
        service.account(4L, "task:task", false);
        assertEquals(1, config.getChapters());
        assertEquals(1, config.getTasks());
    }

    @Test
    void frozenSiteStopsBeforeAnyControllerCallsEvenForGlobalQueue() {
        queue.setSite(null);
        when(sites.isManuallyFrozen(eq(3L), any())).thenReturn(true);
        assertThrows(CrawlerHttpClient.SiteManuallyFrozenException.class,
                () -> service.execute(4L, site, () -> "unused"));
        verifyNoInteractions(api);
    }

    @Test
    void anotherAccountCannotReadOrBrowseConfiguration() {
        User other = User.builder().id(9L).build();
        assertThrows(ResponseStatusException.class, () -> service.get(other, 2L, 4L));
        verifyNoInteractions(api);
    }

    @Test
    void manualSwitchWaitsForInflightRequest() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> request = pool.submit(() -> {
                try {
                    service.execute(4L, site, () -> {
                        entered.countDown();
                        assertTrue(release.await(3, TimeUnit.SECONDS));
                        assertEquals("A", actual);
                        return "ok";
                    });
                } catch (Exception exception) { throw new RuntimeException(exception); }
            });
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            Future<?> change = pool.submit(() -> {
                try { service.switchManually(user, 2L, 4L, "B"); }
                catch (Exception exception) { throw new RuntimeException(exception); }
            });
            assertThrows(TimeoutException.class, () -> change.get(100, TimeUnit.MILLISECONDS));
            release.countDown();
            request.get(3, TimeUnit.SECONDS);
            change.get(3, TimeUnit.SECONDS);
            assertEquals("B", actual);
        }
    }

    @Test
    void refusesDirectNodeAndDuplicateGroupAndNeverReturnsSecret() throws Exception {
        PolicyPayload draft = new PolicyPayload("http://localhost:9111/", "", false,
                "http://localhost:7895", "crawler", List.of("DIRECT"), true, 2, 300, 0, 0, 0, false);
        assertThrows(ResponseStatusException.class, () -> service.save(user, 2L, 4L, draft));
        assertTrue(service.get(user, 2L, 4L).secretConfigured());
        assertFalse(mapper.writeValueAsString(service.get(user, 2L, 4L)).contains("private-secret"));
        when(policies.existsByControllerUrlAndGroupNameAndExecutorIdNot(anyString(), anyString(), anyLong()))
                .thenReturn(true);
        assertThrows(ResponseStatusException.class, () -> service.save(user, 2L, 4L, draft));
    }

    @Test
    void managedHttpRequestsUseFreshConnectionsAfterNodeRotation() throws Exception {
        HttpServer proxy = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<Integer> connections = new ArrayList<>();
        proxy.createContext("/", exchange -> {
            connections.add(exchange.getRemoteAddress().getPort());
            byte[] body = "<html>chapter content</html>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        proxy.start();
        try {
            CrawlerHttpClient http = managedHttp(proxy.getAddress().getPort());
            assertEquals(200, http.get(site, "http://novel.example.com/chapter/1").statusCode());
            config.setRotationChapters(1);
            config.setChapters(1);
            assertEquals(200, http.get(site, "http://novel.example.com/chapter/2").statusCode());
            assertEquals("B", actual);
            assertEquals(2, connections.size());
            assertNotEquals(connections.get(0), connections.get(1));
            assertEquals(0, http.cachedHttpClientCount());
        } finally {
            proxy.stop(0);
        }
    }

    @Test
    void actualHttp403CoolsWebsiteAndDoesNotSwitchNodes() throws Exception {
        HttpServer proxy = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        proxy.createContext("/", exchange -> {
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
        });
        proxy.start();
        try {
            CrawlerHttpClient http = managedHttp(proxy.getAddress().getPort());
            assertThrows(CrawlerHttpClient.NoAvailableQueueProxyException.class,
                    () -> http.get(site, "http://novel.example.com/chapter/1"));
            assertTrue(http.protectionState(site).coolingDown());
            verify(api, never()).select(anyString(), any(), anyString(), anyString());
        } finally {
            proxy.stop(0);
        }
    }

    private CrawlerHttpClient managedHttp(int port) {
        config.setProxyUrl("http://127.0.0.1:" + port);
        site.setBaseUrl("http://novel.example.com");
        site.setRespectRobotsTxt(false);
        site.setRequestIntervalMillis(0);
        site.setRandomDelayMillis(0);
        CrawlerQueueExecutorService route = mock(CrawlerQueueExecutorService.class);
        when(route.isMihomoBound()).thenReturn(true);
        when(route.hasBoundExecutor()).thenReturn(true);
        when(route.boundExecutorId()).thenReturn(4L);
        when(route.availableCandidates(any())).thenReturn(List.of(
                new CrawlerQueueExecutorService.ProxyCandidate("mihomo:4", "Mihomo", config.getProxyUrl(), null)));
        CrawlerHttpClient http = new CrawlerHttpClient(mapper, mock(ProxySettingsService.class),
                mock(CrawlerSettingsService.class), sites);
        ReflectionTestUtils.setField(http, "queueExecutorService", route);
        ReflectionTestUtils.setField(http, "mihomoService", service);
        return http;
    }
}
