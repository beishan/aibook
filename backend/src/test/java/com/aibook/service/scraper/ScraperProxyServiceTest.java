package com.aibook.service.scraper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aibook.model.entity.SystemProxyConfig;
import com.aibook.repository.CrawlerMihomoPolicyRepository;
import com.aibook.repository.SystemProxyConfigRepository;
import com.aibook.service.SystemMihomoService;
import com.aibook.service.crawler.MihomoApiClient;
import com.aibook.service.scraper.ScraperProxyConfigStore.Settings;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

class ScraperProxyServiceTest {
    private final ScraperProxyConfigStore store = mock(ScraperProxyConfigStore.class);
    private final SystemProxyConfigRepository proxies = mock(SystemProxyConfigRepository.class);
    private final SystemMihomoService mihomo = mock(SystemMihomoService.class);
    private final MihomoApiClient api = mock(MihomoApiClient.class);
    private final CrawlerMihomoPolicyRepository policies = mock(CrawlerMihomoPolicyRepository.class);
    private final ScraperProxyService service = new ScraperProxyService(store, proxies, mihomo, api, policies);

    private Settings settings(String mode, String switching, boolean failover, int threshold) {
        return new Settings(mode, 1L, 2L, switching, "B", failover, threshold, 300, 30, 1, 1, false);
    }

    private ObjectNode initialize(Settings settings) throws Exception {
        when(store.read("douban")).thenReturn(new ScraperProxyConfigStore.Saved(7L, settings));
        when(mihomo.resolve(1L, 2L, 7L)).thenReturn(new SystemMihomoService.ResolvedConnection(
                "http://127.0.0.1:9090", "secret", "http://127.0.0.1:7890", "G", List.of("A", "B")));
        ObjectNode catalog = (ObjectNode) new ObjectMapper().readTree("""
                {"proxies":{"G":{"type":"Selector","now":"A","all":["A","B"]},
                "A":{"type":"Shadowsocks"},"B":{"type":"Shadowsocks"}}}
                """);
        when(api.proxies(anyString(), any())).thenReturn(catalog);
        doAnswer(invocation -> {
            ((ObjectNode) catalog.path("proxies").path("G")).put("now", (String) invocation.getArgument(3));
            return null;
        }).when(api).select(anyString(), any(), anyString(), anyString());
        return catalog;
    }

    @Test
    void noneMeansExplicitDirectAndFixedUsesOnlyTheSelectedProxy() throws Exception {
        initialize(Settings.direct());
        service.execute("douban", proxy -> {
            assertThat(proxy).isSameAs(Proxy.NO_PROXY);
            return new ScraperProxyService.Outcome<>("ok", true);
        });
        verifyNoInteractions(proxies);
        when(store.read("douban")).thenReturn(new ScraperProxyConfigStore.Saved(7L,
                settings("FIXED", "MANUAL", false, 2)));
        when(proxies.findById(1L)).thenReturn(Optional.of(SystemProxyConfig.builder().id(1L)
                .name("固定代理").url("http://127.0.0.1:8899").proxyType(SystemProxyConfig.ProxyType.SIMPLE).build()));
        service.execute("douban", proxy -> {
            assertThat(proxy.type()).isEqualTo(Proxy.Type.HTTP);
            assertThat(((InetSocketAddress) proxy.address()).getPort()).isEqualTo(8899);
            return new ScraperProxyService.Outcome<>("ok", true);
        });
    }

    @Test
    void deletedOrDisabledFixedProxyNeverFallsBackToDirect() {
        when(store.read("douban")).thenReturn(new ScraperProxyConfigStore.Saved(7L,
                settings("FIXED", "MANUAL", false, 2)));
        when(proxies.findById(1L)).thenReturn(Optional.empty());
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> service.execute("douban", proxy -> {
            calls.incrementAndGet();
            return new ScraperProxyService.Outcome<>(null, false);
        })).isInstanceOf(ResponseStatusException.class);
        assertThat(calls).hasValue(0);
    }

    @Test
    void requestThresholdRotatesBeforeNextRequestAndManualStaysFixed() throws Exception {
        initialize(settings("MIHOMO", "REQUEST", false, 2));
        service.execute("douban", proxy -> new ScraperProxyService.Outcome<>("one", true));
        assertThat(service.get("douban").currentNode()).isEqualTo("A");
        service.execute("douban", proxy -> new ScraperProxyService.Outcome<>("two", true));
        assertThat(service.get("douban").currentNode()).isEqualTo("B");
        verify(api).select(anyString(), any(), eq("G"), eq("B"));
        initialize(settings("MIHOMO", "MANUAL", true, 2));
        service.execute("douban", proxy -> new ScraperProxyService.Outcome<>("three", true));
        service.execute("douban", proxy -> new ScraperProxyService.Outcome<>("four", true));
        assertThat(service.get("douban").currentNode()).isEqualTo("B");
    }

    @Test
    @SuppressWarnings("unchecked")
    void timeRotationCountsActiveRequestsAndIgnoresIdleTime() throws Exception {
        initialize(settings("MIHOMO", "TIME", false, 2));
        service.execute("douban", proxy -> new ScraperProxyService.Outcome<>("ok", true));
        assertThat(service.get("douban").currentNode()).isEqualTo("A");
        var states = (java.util.Map<String, ?>) org.springframework.test.util.ReflectionTestUtils.getField(service, "runtimes");
        org.springframework.test.util.ReflectionTestUtils.setField(states.get("douban"), "activeMillis", 30000L);
        service.execute("douban", proxy -> new ScraperProxyService.Outcome<>("ok", true));
        assertThat(service.get("douban").currentNode()).isEqualTo("B");
    }

    @Test
    void taskThresholdIsSeparateFromSuccessfulHttpRequests() throws Exception {
        initialize(settings("MIHOMO", "TASK", false, 2));
        service.execute("douban", proxy -> new ScraperProxyService.Outcome<>(null, true));
        service.execute("douban", proxy -> new ScraperProxyService.Outcome<>(null, true));
        assertThat(service.get("douban").currentNode()).isEqualTo("A");
        service.taskSucceeded("douban");
        service.execute("douban", proxy -> new ScraperProxyService.Outcome<>(null, true));
        assertThat(service.get("douban").currentNode()).isEqualTo("B");
    }

    @Test
    void networkFailureFreezesNodeAndFailoverRemainsBounded() throws Exception {
        initialize(settings("MIHOMO", "FAILOVER", true, 1));
        AtomicInteger calls = new AtomicInteger();
        service.execute("douban", proxy -> {
            if (calls.incrementAndGet() == 1) throw new IOException("network failure");
            return new ScraperProxyService.Outcome<>("ok", true);
        });
        assertThat(calls).hasValue(2);
        assertThat(service.get("douban").currentNode()).isEqualTo("B");
        assertThat(service.get("douban").cooldowns()).containsKey("A");
    }

    @Test
    void siteDenialAndRateLimitsDoNotTriggerNetworkFailoverOrSuccessfulCounts() throws Exception {
        initialize(settings("MIHOMO", "FAILOVER", true, 1));
        assertThatThrownBy(() -> service.execute("douban", proxy -> {
            throw new HttpClientErrorException(HttpStatus.FORBIDDEN);
        })).isInstanceOf(HttpClientErrorException.class);
        service.execute("douban", proxy -> new ScraperProxyService.Outcome<>(null, false));
        assertThat(service.get("douban").requests()).isZero();
        assertThat(service.get("douban").cooldowns()).isEmpty();
        verify(api, never()).select(anyString(), any(), anyString(), anyString());
    }

    @Test
    void disabledFailoverWaitsForFrozenNodeRatherThanChangingIt() throws Exception {
        initialize(settings("MIHOMO", "REQUEST", false, 1));
        assertThatThrownBy(() -> service.execute("douban", proxy -> {
            throw new IOException("failure");
        })).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> service.execute("douban", proxy -> new ScraperProxyService.Outcome<>("ok", true)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("冻结");
        verify(api, never()).select(anyString(), any(), anyString(), anyString());
    }

    @Test
    void rejectsExecutorConflictsAndInvalidManualNodeBeforeSaving() throws Exception {
        initialize(settings("MIHOMO", "MANUAL", false, 2));
        when(policies.existsByControllerUrlAndGroupNameAndExecutorIdNot(anyString(), anyString(), anyLong())).thenReturn(true);
        assertThatThrownBy(() -> service.save("douban", 7L, settings("MIHOMO", "MANUAL", false, 2)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("独立");
        verify(store, never()).save(any(), any(), any());
        when(policies.existsByControllerUrlAndGroupNameAndExecutorIdNot(anyString(), anyString(), anyLong())).thenReturn(false);
        var invalid = new Settings("MIHOMO", 1L, 2L, "MANUAL", "MISSING", false, 2, 300, 30, 1, 1, false);
        assertThatThrownBy(() -> service.save("douban", 7L, invalid))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("手动节点");
    }
}
