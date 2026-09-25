package com.aibook.dto.backup;

public record BackupRetentionSettings(
        boolean enabled,
        int recentDays,
        int monthlyMonths) {
}
