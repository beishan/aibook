package com.aibook.service.repair;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 修复模块初始化器
 * 在应用启动时初始化系统默认规则和模板
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TextRepairInitializer {

    private final TextRepairRuleService ruleService;
    private final TextRepairTemplateService templateService;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        try {
            ruleService.initSystemRules();
            templateService.initSystemTemplates();
            log.info("TXT 内容修复模块初始化完成");
        } catch (Exception e) {
            log.warn("TXT 内容修复模块初始化失败: {}", e.getMessage());
        }
    }
}
