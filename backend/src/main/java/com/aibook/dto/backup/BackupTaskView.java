package com.aibook.dto.backup;

import com.aibook.model.entity.BackupTask;
import java.time.LocalDateTime;

public record BackupTaskView(
        Long id,
        String name,
        boolean databaseEnabled,
        boolean booksEnabled,
        boolean uploadsEnabled,
        boolean crawlerDataEnabled,
        boolean scheduleEnabled,
        boolean enabled,
        String cronExpression,
        LocalDateTime nextRunAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static BackupTaskView from(BackupTask task) {
        return new BackupTaskView(
                task.getId(), task.getName(), task.isDatabaseEnabled(),
                task.isBooksEnabled(), task.isUploadsEnabled(), task.isCrawlerDataEnabled(),
                task.isScheduleEnabled(), task.isEnabled(), task.getCronExpression(),
                task.getNextRunAt(), task.getCreatedAt(), task.getUpdatedAt());
    }
}
