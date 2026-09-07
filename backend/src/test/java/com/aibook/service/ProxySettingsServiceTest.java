package com.aibook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aibook.dto.ProxySettingsDtos.CrawlerProxyRequest;
import com.aibook.model.entity.CrawlerProxyConfig;
import com.aibook.model.entity.SystemProxyConfig;
import com.aibook.repository.CrawlerProxyConfigRepository;
import com.aibook.repository.SystemProxyConfigRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProxySettingsServiceTest {

    @Test
    void returnsEffectiveCrawlerProxiesInPriorityOrderAndSkipsDisabledReferences() {
        SystemProxyConfigRepository systems = mock(SystemProxyConfigRepository.class);
        CrawlerProxyConfigRepository crawlers = mock(CrawlerProxyConfigRepository.class);
        SystemProxyConfig enabled = SystemProxyConfig.builder().id(1L).name("系统 A")
                .url("http://system-a:8080").enabled(true).priority(50).build();
        SystemProxyConfig disabled = SystemProxyConfig.builder().id(2L).name("系统 B")
                .url("http://system-b:8080").enabled(false).priority(10).build();
        when(systems.findAll()).thenReturn(List.of(enabled, disabled));
        when(crawlers.findAllByOrderByPriorityAscIdAsc()).thenReturn(List.of(
                CrawlerProxyConfig.builder().id(11L).systemProxyId(2L).enabled(true).priority(1).build(),
                CrawlerProxyConfig.builder().id(12L).name("独立代理").url("http://custom:8080").enabled(true).priority(5).build(),
                CrawlerProxyConfig.builder().id(13L).systemProxyId(1L).enabled(true).priority(10).build()));
        ProxySettingsService service = new ProxySettingsService(systems, crawlers);

        assertThat(service.activeCrawlerProxyUrls())
                .containsExactly("http://custom:8080", "http://system-a:8080");
    }

    @Test
    void referencedCrawlerProxyBecomesUnavailableWhenSystemProxyIsDisabled() {
        SystemProxyConfigRepository systems = mock(SystemProxyConfigRepository.class);
        CrawlerProxyConfigRepository crawlers = mock(CrawlerProxyConfigRepository.class);
        SystemProxyConfig disabled = SystemProxyConfig.builder().id(2L).name("已停用")
                .url("http://disabled:8080").enabled(false).priority(10).build();
        CrawlerProxyConfig reference = CrawlerProxyConfig.builder().id(20L)
                .systemProxyId(2L).enabled(true).priority(20).build();
        when(systems.findAll()).thenReturn(List.of(disabled));
        when(crawlers.findAllByOrderByPriorityAscIdAsc()).thenReturn(List.of(reference));
        ProxySettingsService service = new ProxySettingsService(systems, crawlers);

        var view = service.crawlerProxies().getFirst();

        assertThat(view.sourceAvailable()).isFalse();
        assertThat(view.effectiveEnabled()).isFalse();
    }

    @Test
    void createsReferenceWithoutCopyingSystemProxyAddress() {
        SystemProxyConfigRepository systems = mock(SystemProxyConfigRepository.class);
        CrawlerProxyConfigRepository crawlers = mock(CrawlerProxyConfigRepository.class);
        SystemProxyConfig system = SystemProxyConfig.builder().id(7L).name("共享代理")
                .url("http://shared:7890").enabled(true).priority(30).build();
        when(systems.findById(7L)).thenReturn(Optional.of(system));
        when(crawlers.save(any())).thenAnswer(invocation -> {
            CrawlerProxyConfig value = invocation.getArgument(0);
            value.setId(9L);
            return value;
        });
        ProxySettingsService service = new ProxySettingsService(systems, crawlers);

        var view = service.createCrawlerProxy(new CrawlerProxyRequest(null, null, 7L, true, 5));

        assertThat(view.systemProxyId()).isEqualTo(7L);
        assertThat(view.url()).isEqualTo("http://shared:7890");
        assertThat(view.effectiveEnabled()).isTrue();
    }
}
