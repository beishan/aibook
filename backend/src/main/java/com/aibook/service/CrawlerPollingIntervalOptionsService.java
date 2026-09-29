package com.aibook.service;

import java.util.List;
import java.util.TreeSet;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CrawlerPollingIntervalOptionsService {
    private static final String CONFIG_KEY = "crawler.ui.pollingIntervalSeconds";
    private static final List<Integer> DEFAULT_OPTIONS = List.of(1, 3, 5, 10, 30);
    private final SystemConfigService systemConfigService;

    public CrawlerPollingIntervalOptionsService(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    public List<Integer> getOptions() {
        String configured = systemConfigService.getConfig(CONFIG_KEY, null);
        if (configured == null || configured.isBlank()) return DEFAULT_OPTIONS;
        try {
            return normalize(java.util.Arrays.stream(configured.split(","))
                    .map(String::trim)
                    .map(Integer::valueOf)
                    .toList());
        } catch (RuntimeException exception) {
            return DEFAULT_OPTIONS;
        }
    }

    public List<Integer> updateOptions(List<Integer> requested) {
        List<Integer> normalized = normalize(requested);
        String value = normalized.stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
        systemConfigService.saveConfig(CONFIG_KEY, value, "采集页面自动刷新可选间隔（秒）");
        return normalized;
    }

    private List<Integer> normalize(List<Integer> requested) {
        if (requested == null || requested.isEmpty() || requested.size() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "自动刷新间隔需要配置 1–20 个选项");
        }
        TreeSet<Integer> options = new TreeSet<>();
        for (Integer interval : requested) {
            if (interval == null || interval < 1 || interval > 3600) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "自动刷新间隔必须是 1–3600 秒的整数");
            }
            if (!options.add(interval)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "自动刷新间隔不能重复");
            }
        }
        return List.copyOf(options);
    }
}
