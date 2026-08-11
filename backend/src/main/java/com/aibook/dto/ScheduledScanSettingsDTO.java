package com.aibook.dto;

/** 用户每日定时扫描配置。 */
public record ScheduledScanSettingsDTO(boolean enabled, String time) {
}
