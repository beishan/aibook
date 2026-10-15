package com.aibook.service.scraper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aibook.model.entity.MihomoNodeGroup;
import com.aibook.model.entity.SystemProxyConfig;
import com.aibook.repository.MihomoNodeGroupRepository;
import com.aibook.repository.SystemProxyConfigRepository;
import com.aibook.service.SystemConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScraperProxyConfigStoreTest {
    @Test
    void savesOnlyReferencesAndOwnerAndDefaultsEachSourceToDirect() {
        var configs = mock(SystemConfigService.class);
        Map<String, String> saved = new HashMap<>();
        when(configs.getConfig(anyString(), anyString())).thenAnswer(call ->
                saved.getOrDefault(call.getArgument(0), call.getArgument(1)));
        doAnswer(call -> {
            saved.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(configs).saveConfig(anyString(), anyString(), anyString());
        var proxies = mock(SystemProxyConfigRepository.class);
        var groups = mock(MihomoNodeGroupRepository.class);
        var store = new ScraperProxyConfigStore(configs, new ObjectMapper(), proxies, groups);
        assertThat(store.read("douban").settings().mode()).isEqualTo("NONE");
        var settings = new ScraperProxyConfigStore.Settings("MIHOMO", 1L, 2L, "MANUAL", "A", false,
                2, 300, 300, 20, 1, false);
        store.save("douban", 7L, settings);
        assertThat(store.read("douban").ownerId()).isEqualTo(7L);
        assertThat(store.read("douban").settings()).isEqualTo(settings);
        assertThat(store.read("google").settings().mode()).isEqualTo("NONE");
        assertThat(saved.keySet()).containsExactly("metadata-proxy.douban");
        assertThat(saved.values().toString()).doesNotContain("controllerSecret", "controllerUrl", "proxyUrl");
        when(proxies.findById(1L)).thenReturn(Optional.of(SystemProxyConfig.builder()
                .controllerUrl("http://controller:9090").build()));
        var group = new MihomoNodeGroup();
        group.setControlGroup("G");
        when(groups.findById(2L)).thenReturn(Optional.of(group));
        assertThat(store.usesSystemProxy(1L)).isTrue();
        assertThat(store.usesNodeGroup(2L)).isTrue();
        assertThat(store.controls("http://controller:9090", "G", "google")).isTrue();
        assertThat(store.controls("http://controller:9090", "G", "douban")).isFalse();
    }

    @Test
    void corruptSavedConfigIsNotSilentlyTreatedAsDirect() {
        var configs = mock(SystemConfigService.class);
        when(configs.getConfig(anyString(), anyString())).thenReturn("{broken");
        var store = new ScraperProxyConfigStore(configs, new ObjectMapper(),
                mock(SystemProxyConfigRepository.class), mock(MihomoNodeGroupRepository.class));
        assertThatThrownBy(() -> store.read("douban")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
