package com.aibook.service;

import com.aibook.dto.ProxySettingsDtos.*;
import com.aibook.model.entity.CrawlerProxyConfig;
import com.aibook.model.entity.SystemProxyConfig;
import com.aibook.repository.CrawlerProxyConfigRepository;
import com.aibook.repository.SystemProxyConfigRepository;
import java.net.URI;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ProxySettingsService {
    private final SystemProxyConfigRepository systemRepository;
    private final CrawlerProxyConfigRepository crawlerRepository;

    @Transactional(readOnly = true)
    public List<SystemProxyView> systemProxies() {
        return systemRepository.findAllByOrderByPriorityAscIdAsc().stream().map(this::systemView).toList();
    }

    @Transactional
    public SystemProxyView createSystemProxy(SystemProxyRequest request) {
        return systemView(systemRepository.save(apply(new SystemProxyConfig(), request)));
    }

    @Transactional
    public SystemProxyView updateSystemProxy(Long id, SystemProxyRequest request) {
        SystemProxyConfig config = systemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "系统代理配置不存在"));
        return systemView(systemRepository.save(apply(config, request)));
    }

    @Transactional
    public void deleteSystemProxy(Long id) {
        if (!systemRepository.existsById(id))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "系统代理配置不存在");
        systemRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<CrawlerProxyView> crawlerProxies() {
        Map<Long, SystemProxyConfig> systems = systemMap();
        return crawlerRepository.findAllByOrderByPriorityAscIdAsc().stream()
                .map(config -> crawlerView(config, systems.get(config.getSystemProxyId()))).toList();
    }

    @Transactional
    public CrawlerProxyView createCrawlerProxy(CrawlerProxyRequest request) {
        CrawlerProxyConfig config = crawlerRepository.save(apply(new CrawlerProxyConfig(), request));
        return crawlerView(config, referencedSystem(config));
    }

    @Transactional
    public CrawlerProxyView updateCrawlerProxy(Long id, CrawlerProxyRequest request) {
        CrawlerProxyConfig config = crawlerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "爬虫代理配置不存在"));
        config = crawlerRepository.save(apply(config, request));
        return crawlerView(config, referencedSystem(config));
    }

    @Transactional
    public void deleteCrawlerProxy(Long id) {
        if (!crawlerRepository.existsById(id))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "爬虫代理配置不存在");
        crawlerRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<String> activeCrawlerProxyUrls() {
        Map<Long, SystemProxyConfig> systems = systemMap();
        return crawlerRepository.findAllByOrderByPriorityAscIdAsc().stream()
                .filter(config -> Boolean.TRUE.equals(config.getEnabled()))
                .map(config -> effectiveUrl(config, systems.get(config.getSystemProxyId())))
                .filter(Objects::nonNull).distinct().toList();
    }

    private SystemProxyConfig apply(SystemProxyConfig config, SystemProxyRequest request) {
        config.setName(required(request.name(), "请输入代理名称"));
        config.setUrl(validateUrl(request.url()));
        config.setEnabled(request.enabled() == null || request.enabled());
        config.setPriority(priority(request.priority()));
        return config;
    }

    private CrawlerProxyConfig apply(CrawlerProxyConfig config, CrawlerProxyRequest request) {
        config.setEnabled(request.enabled() == null || request.enabled());
        config.setPriority(priority(request.priority()));
        if (request.systemProxyId() != null) {
            SystemProxyConfig system = systemRepository.findById(request.systemProxyId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "引用的系统代理不存在"));
            config.setSystemProxyId(system.getId());
            config.setName(null);
            config.setUrl(null);
        } else {
            config.setSystemProxyId(null);
            config.setName(required(request.name(), "请输入代理名称"));
            config.setUrl(validateUrl(request.url()));
        }
        return config;
    }

    private SystemProxyView systemView(SystemProxyConfig config) {
        return new SystemProxyView(config.getId(), config.getName(), config.getUrl(),
                Boolean.TRUE.equals(config.getEnabled()), priority(config.getPriority()),
                config.getCreatedAt(), config.getUpdatedAt());
    }

    private CrawlerProxyView crawlerView(CrawlerProxyConfig config, SystemProxyConfig system) {
        boolean reference = config.getSystemProxyId() != null;
        boolean sourceAvailable = !reference || system != null && Boolean.TRUE.equals(system.getEnabled());
        String name = reference && system != null ? system.getName() : config.getName();
        String url = reference && system != null ? system.getUrl() : config.getUrl();
        return new CrawlerProxyView(config.getId(), reference ? "SYSTEM" : "CUSTOM", name, url,
                config.getSystemProxyId(), system == null ? null : system.getName(),
                Boolean.TRUE.equals(config.getEnabled()), sourceAvailable,
                Boolean.TRUE.equals(config.getEnabled()) && sourceAvailable,
                priority(config.getPriority()), config.getCreatedAt(), config.getUpdatedAt());
    }

    private String effectiveUrl(CrawlerProxyConfig config, SystemProxyConfig system) {
        if (config.getSystemProxyId() == null) return config.getUrl();
        return system != null && Boolean.TRUE.equals(system.getEnabled()) ? system.getUrl() : null;
    }

    private SystemProxyConfig referencedSystem(CrawlerProxyConfig config) {
        return config.getSystemProxyId() == null ? null : systemRepository.findById(config.getSystemProxyId()).orElse(null);
    }

    private Map<Long, SystemProxyConfig> systemMap() {
        Map<Long, SystemProxyConfig> values = new HashMap<>();
        systemRepository.findAll().forEach(config -> values.put(config.getId(), config));
        return values;
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        return value.trim();
    }

    private String validateUrl(String value) {
        String normalized = required(value, "请输入代理地址");
        try {
            URI uri = URI.create(normalized.contains("://") ? normalized : "http://" + normalized);
            if (uri.getHost() == null || uri.getPort() < 1 || !Set.of("http", "https").contains(uri.getScheme()))
                throw new IllegalArgumentException();
            return normalized;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "代理地址必须包含有效的主机和端口");
        }
    }

    private int priority(Integer value) {
        int priority = value == null ? 100 : value;
        if (priority < 1 || priority > 9999)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "代理优先级必须在 1 到 9999 之间");
        return priority;
    }
}
