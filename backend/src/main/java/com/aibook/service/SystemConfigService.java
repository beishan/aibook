package com.aibook.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 系统配置服务
 */
@Service
public class SystemConfigService {

    private final Map<String, String> configs = new HashMap<>();

    public SystemConfigService() {
        // 默认配置
        configs.put("scraping.enabled", "true");
        configs.put("scraping.interval", "3600");
        configs.put("scraping.user_agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
    }

    /**
     * 获取配置值
     */
    public String getConfig(String key) {
        return configs.get(key);
    }

    /**
     * 获取配置值，带默认值
     */
    public String getConfig(String key, String defaultValue) {
        return configs.getOrDefault(key, defaultValue);
    }

    /**
     * 设置配置值
     */
    public void setConfig(String key, String value) {
        configs.put(key, value);
    }

    /**
     * 获取所有配置
     */
    public Map<String, String> getAllConfigs() {
        return new HashMap<>(configs);
    }

    /**
     * 获取布尔配置值
     */
    public boolean getBooleanConfig(String key, boolean defaultValue) {
        String value = configs.get(key);
        if (value == null) return defaultValue;
        return Boolean.parseBoolean(value);
    }

    /**
     * 获取整数配置值
     */
    public int getIntConfig(String key, int defaultValue) {
        String value = configs.get(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
