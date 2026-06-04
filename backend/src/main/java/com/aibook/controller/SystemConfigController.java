package com.aibook.controller;

import com.aibook.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统配置控制器
 */
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService configService;

    /**
     * 获取刮削配置
     */
    @GetMapping("/scraper")
    public ResponseEntity<Map<String, String>> getScraperConfig() {
        Map<String, String> configs = configService.getConfigsByPrefix("scraper.");
        return ResponseEntity.ok(configs);
    }

    /**
     * 更新刮削配置
     */
    @PutMapping("/scraper")
    public ResponseEntity<Void> updateScraperConfig(@RequestBody Map<String, String> configs) {
        // 过滤只允许 scraper. 前缀的配置
        Map<String, String> filteredConfigs = configs.entrySet().stream()
                .filter(e -> e.getKey().startsWith("scraper."))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        configService.saveConfigs(filteredConfigs);
        return ResponseEntity.ok().build();
    }
}
