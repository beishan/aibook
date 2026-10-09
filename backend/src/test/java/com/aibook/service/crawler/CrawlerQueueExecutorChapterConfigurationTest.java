package com.aibook.service.crawler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aibook.dto.ProxySettingsDtos.CrawlerProxyView;
import com.aibook.model.entity.*;
import com.aibook.repository.*;
import com.aibook.service.CrawlerSettingsService;
import com.aibook.service.ProxySettingsService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CrawlerQueueExecutorChapterConfigurationTest {
    @Test
    void selectedProxyAndModeChangesApplyOnlyAfterChapterScopeCloses() throws Exception {
        var queues = mock(CrawlerTaskQueueRepository.class);
        var executors = mock(CrawlerQueueExecutorRepository.class);
        var bindings = mock(CrawlerQueueExecutorProxyRepository.class);
        var cooldowns = mock(CrawlerQueueProxyCooldownRepository.class);
        var proxies = mock(ProxySettingsService.class);
        var mihomo = mock(CrawlerMihomoService.class);
        var service = new CrawlerQueueExecutorService(queues, executors, bindings, cooldowns,
                proxies, mock(CrawlerSettingsService.class), mihomo);
        var site = CrawlerSite.builder().id(3L).build();
        var queue = CrawlerTaskQueue.builder().id(2L).site(site).build();
        var executor = CrawlerQueueExecutor.builder().id(4L).queue(queue)
                .proxyMode(CrawlerQueueExecutor.ProxyMode.SELECTED).build();
        var first = CrawlerQueueExecutorProxy.builder().executor(executor)
                .proxyConfigId(10L).cooldownSeconds(100).build();
        var second = CrawlerQueueExecutorProxy.builder().executor(executor)
                .proxyConfigId(11L).cooldownSeconds(200).build();
        when(executors.findById(4L)).thenReturn(Optional.of(executor));
        when(cooldowns.findByQueueId(2L)).thenReturn(List.of());
        when(proxies.crawlerProxies()).thenReturn(List.of(proxy(10L, 7890), proxy(11L, 7891)));
        when(bindings.findByExecutorIdOrderBySortOrderAscIdAsc(4L)).thenReturn(List.of(first));
        try (var route = service.bind(2L, 4L)) {
            try (var chapter = service.beginChapter(site)) {
                assertEquals("http://localhost:7890", service.availableCandidates(site).getFirst().url());
                when(bindings.findByExecutorIdOrderBySortOrderAscIdAsc(4L)).thenReturn(List.of(second));
                assertEquals("http://localhost:7890", service.availableCandidates(site).getFirst().url());
                assertEquals(100, service.availableCandidates(site).getFirst().cooldownSeconds());
                executor.setProxyMode(CrawlerQueueExecutor.ProxyMode.MIHOMO);
                assertFalse(service.isMihomoBound());
                assertEquals("http://localhost:7890", service.availableCandidates(site).getFirst().url());
            }
            when(mihomo.beginChapter(4L)).thenReturn(() -> { });
            when(mihomo.proxyUrl(4L)).thenReturn("http://localhost:7892");
            try (var next = service.beginChapter(site)) {
                assertTrue(service.isMihomoBound());
                assertEquals("http://localhost:7892", service.availableCandidates(site).getFirst().url());
            }
            executor.setProxyMode(CrawlerQueueExecutor.ProxyMode.SELECTED);
            try (var next = service.beginChapter(site)) {
                assertEquals("http://localhost:7891", service.availableCandidates(site).getFirst().url());
                assertEquals(200, service.availableCandidates(site).getFirst().cooldownSeconds());
            }
        }
        assertFalse(service.hasBoundExecutor());
    }

    private CrawlerProxyView proxy(Long id, int port) {
        return new CrawlerProxyView(id, "CUSTOM", "proxy " + id, "http://localhost:" + port,
                null, null, true, true, true, 0, null, null);
    }
}
