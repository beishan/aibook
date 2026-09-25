package com.aibook.dto.backup;

public record BackupTaskRequest(
        String name,
        boolean databaseEnabled,
        boolean booksEnabled,
        boolean uploadsEnabled,
        boolean crawlerDataEnabled,
        boolean scheduleEnabled,
        boolean enabled,
        String cronExpression) {}
