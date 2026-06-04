package com.aibook.service;

import com.aibook.model.entity.SystemConfig;
import com.aibook.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统配置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigRepository configRepository;
    private final Map<String, String> configs = new HashMap<>();

    public SystemConfigService() {
        // 默认配置
        configs.put("scraping.enabled", "true");
        configs.put("scraping.interval", "3600");
        configs.put("scraping.user_agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
    }

    /**
     * 获取指定前缀的所有配置
     * 获取配置值
     */
    public Map<String, String> getConfigsByPrefix(String prefix) {
        List<SystemConfig> configs = configRepository.findByConfigKeyStartingWith(prefix);
        return configs.stream()
                .collect(Collectors.toMap(
                        SystemConfig::getConfigKey,
                        c -> c.getConfigValue() != null ? c.getConfigValue() : ""
                ));
    public String getConfig(String key) {
        return configs.get(key);
    }

    /**
     * 获取单个配置值
     * 获取配置值，带默认值
     */
    public String getConfig(String key, String defaultValue) {
        return configRepository.findById(key)
                .map(SystemConfig::getConfigValue)
                .orElse(defaultValue);
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
     * 获取布尔配置
     */
    public boolean getBooleanConfig(String key, boolean defaultValue) {
        String value = getConfig(key, null);
        if (value == null) {
            return defaultValue;
        }
        String value = configs.get(key);
        if (value == null) return defaultValue;
        return Boolean.parseBoolean(value);
    }

    /**
     * 获取整数配置
     * 获取整数配置值
     */
    public int getIntConfig(String key, int defaultValue) {
        String value = getConfig(key, null);
        if (value == null) {
            return defaultValue;
        }
        String value = configs.get(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 批量保存配置
     */
    @Transactional
    public void saveConfigs(Map<String, String> configs) {
        configs.forEach((key, value) -> {
            SystemConfig config = configRepository.findById(key)
                    .orElse(SystemConfig.builder().configKey(key).build());
            config.setConfigValue(value);
            configRepository.save(config);
        });
        log.info("保存配置 {} 项", configs.size());
    }

    /**
     * 保存单个配置
     */
    @Transactional
    public void saveConfig(String key, String value, String description) {
        SystemConfig config = configRepository.findById(key)
                .orElse(SystemConfig.builder().configKey(key).build());
        config.setConfigValue(value);
        if (description != null) {
            config.setDescription(description);
        }
        configRepository.save(config);
    }
}
