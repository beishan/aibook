package com.aibook.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aibook.dto.ProxySettingsDtos.SystemProxyRequest;
import com.aibook.dto.crawler.MihomoDtos.NodeGroupPayload;
import com.aibook.model.entity.*;
import com.aibook.repository.*;
import com.aibook.service.crawler.MihomoApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class SystemMihomoServiceTest {
    private final SystemProxyConfigRepository proxies = mock(SystemProxyConfigRepository.class);
    private final MihomoNodeGroupRepository groups = mock(MihomoNodeGroupRepository.class);
    private final CrawlerMihomoPolicyRepository policies = mock(CrawlerMihomoPolicyRepository.class);
    private final MihomoApiClient api = mock(MihomoApiClient.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final SystemMihomoService service = new SystemMihomoService(proxies, groups, policies, api, mapper);
    private final SystemProxyConfig proxy = SystemProxyConfig.builder().id(7L).name("Mihomo")
            .url("http://mihomo:7890").proxyType(SystemProxyConfig.ProxyType.MIHOMO)
            .controllerUrl("http://mihomo:9111").controllerSecret("private-secret").enabled(true).build();

    @BeforeEach
    void prepare() throws Exception {
        when(proxies.findById(7L)).thenReturn(Optional.of(proxy));
        when(api.proxies(anyString(), any())).thenReturn(mapper.readTree(
                "{\"proxies\":{\"crawler\":{\"type\":\"Selector\",\"now\":\"A\",\"all\":[\"A\",\"B\",\"DIRECT\"]},"
                        + "\"A\":{\"type\":\"SS\"},\"B\":{\"type\":\"SS\"},\"DIRECT\":{\"type\":\"Direct\"}}}"));
        when(groups.save(any())).thenAnswer(call -> {
            MihomoNodeGroup group = call.getArgument(0);
            group.setId(8L);
            return group;
        });
    }

    @Test
    void storesOrderedAccountOwnedGroupAndResolvesLiveCredentials() throws Exception {
        var view = service.save(7L, null, 1L, new NodeGroupPayload("备用", "crawler", "http://mihomo:7895", List.of("B", "A")));
        assertEquals(List.of("B", "A"), view.nodes());
        MihomoNodeGroup stored = group(1L);
        stored.setNodesJson("[\"B\",\"A\"]");
        when(groups.findById(8L)).thenReturn(Optional.of(stored));
        proxy.setControllerSecret("updated-secret");
        var resolved = service.resolve(7L, 8L, 1L);
        assertEquals("updated-secret", resolved.secret());
        assertEquals("http://mihomo:7895", resolved.proxyUrl());
        assertThrows(ResponseStatusException.class, () -> service.resolve(7L, 8L, 2L));
    }

    @Test
    void rejectsDirectDuplicateAndOutOfGroupNodes() {
        for (List<String> nodes : List.of(List.of("DIRECT"), List.of("A", "A"), List.of("foreign"))) {
            assertThrows(ResponseStatusException.class,
                    () -> service.save(7L, null, 1L, new NodeGroupPayload("备用", "crawler", "http://mihomo:7895", nodes)));
        }
        verify(groups, never()).save(any());
    }

    @Test
    void preventsDeletingBoundGroupAndEditingItsRoutingTopology() {
        when(groups.findById(8L)).thenReturn(Optional.of(group(1L)));
        when(policies.existsByNodeGroupId(8L)).thenReturn(true);
        assertThrows(ResponseStatusException.class, () -> service.delete(7L, 8L, 1L));
        assertThrows(ResponseStatusException.class, () -> service.save(7L, 8L, 1L,
                new NodeGroupPayload("备用", "crawler", "http://mihomo:9999", List.of("A", "B"))));
        verify(groups, never()).delete(any());
    }

    @Test
    void disabledOrDeletedSourceCannotResolveForExecution() {
        when(groups.findById(8L)).thenReturn(Optional.of(group(1L)));
        proxy.setEnabled(false);
        assertThrows(ResponseStatusException.class, () -> service.resolve(7L, 8L, 1L));
        when(proxies.findById(7L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.resolve(7L, 8L, 1L));
    }

    @Test
    void browsingDraftRetainsSecretWithoutChangingSavedConnection() throws Exception {
        var draft = new SystemMihomoService.ConnectionPayload(7L, new SystemProxyRequest(
                "Mihomo", "http://mihomo:7890", true, 100, "MIHOMO", "http://mihomo:9111", "", false));
        var catalog = service.browse(draft);
        assertEquals(2, catalog.nodes().size());
        verify(api).proxies("http://mihomo:9111", "private-secret");
        verify(proxies, never()).save(any());
        assertFalse(mapper.writeValueAsString(catalog).contains("private-secret"));
    }

    @Test
    void connectionTestReportsControllerFailureWithoutLeakingRemoteData() throws Exception {
        when(api.proxies(anyString(), any())).thenThrow(new MihomoApiClient.ApiException(401));
        var result = service.test(new SystemMihomoService.ConnectionPayload(7L, new SystemProxyRequest(
                "Mihomo", "http://mihomo:7890", true, 100, "MIHOMO", "http://mihomo:9111", "", false)));
        assertFalse(result.controllerAvailable());
        assertFalse(result.proxyAvailable());
        assertFalse(result.message().contains("private-secret"));
    }

    private MihomoNodeGroup group(Long owner) {
        MihomoNodeGroup group = new MihomoNodeGroup();
        group.setId(8L);
        group.setSystemProxyId(7L);
        group.setUserId(owner);
        group.setName("备用");
        group.setControlGroup("crawler");
        group.setProxyUrl("http://mihomo:7895");
        group.setNodesJson("[\"A\",\"B\"]");
        return group;
    }
}
