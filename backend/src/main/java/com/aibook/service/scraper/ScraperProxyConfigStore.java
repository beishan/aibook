package com.aibook.service.scraper;

import com.aibook.model.entity.MihomoNodeGroup;
import com.aibook.repository.MihomoNodeGroupRepository;
import com.aibook.repository.SystemProxyConfigRepository;
import com.aibook.service.SystemConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 来源代理只保存系统代理/节点组引用，不复制连接密钥。 */
@Service
@RequiredArgsConstructor
public class ScraperProxyConfigStore {
    public static final List<String> SOURCES = List.of("douban", "jd", "google", "openlibrary");
    private final SystemConfigService configs;
    private final ObjectMapper mapper;
    private final SystemProxyConfigRepository proxies;
    private final MihomoNodeGroupRepository groups;

    public record Settings(String mode, Long systemProxyId, Long nodeGroupId, String switchingMode,
            String manualNode, boolean failover, int failureThreshold, int cooldownSeconds,
            int rotationSeconds, int rotationRequests, int rotationTasks, boolean randomOrder) {
        public static Settings direct() {
            return new Settings("NONE", null, null, "MANUAL", null, false, 2, 300, 300, 20, 1, false);
        }
    }
    public record Saved(Long ownerId, Settings settings) {}

    public Saved read(String source) {
        if (source == null || !SOURCES.contains(source)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "未知刮削来源");
        }
        String json = configs.getConfig("metadata-proxy." + source, "");
        if (json == null || json.isBlank()) return new Saved(null, Settings.direct());
        try {
            Saved saved = mapper.readValue(json, Saved.class);
            if (saved.settings() == null) throw new IllegalArgumentException();
            return saved;
        } catch (Exception exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "刮削代理配置无法读取，请重新保存配置");
        }
    }

    public void save(String source, Long ownerId, Settings settings) {
        try {
            configs.saveConfig("metadata-proxy." + source,
                    mapper.writeValueAsString(new Saved(ownerId, settings)), "书籍刮削来源代理");
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("代理配置保存失败");
        }
    }

    public boolean usesSystemProxy(Long id) {
        return SOURCES.stream().map(this::read).anyMatch(saved ->
                !"NONE".equals(saved.settings().mode()) && Objects.equals(saved.settings().systemProxyId(), id));
    }

    public boolean usesNodeGroup(Long id) {
        return SOURCES.stream().map(this::read).anyMatch(saved ->
                "MIHOMO".equals(saved.settings().mode()) && Objects.equals(saved.settings().nodeGroupId(), id));
    }

    public boolean controls(String controller, String controlGroup, String excludingSource) {
        for (String source : SOURCES) {
            if (source.equals(excludingSource)) continue;
            Settings settings = read(source).settings();
            if (!"MIHOMO".equals(settings.mode())) continue;
            var proxy = proxies.findById(settings.systemProxyId()).orElse(null);
            MihomoNodeGroup group = groups.findById(settings.nodeGroupId()).orElse(null);
            if (proxy != null && group != null && Objects.equals(proxy.getControllerUrl(), controller)
                    && Objects.equals(group.getControlGroup(), controlGroup)) return true;
        }
        return false;
    }
}
