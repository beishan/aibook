package com.aibook.dto.backup;

import com.aibook.model.entity.BackupExecution;
import java.time.LocalDateTime;

public record BackupExecutionView(
        Long id,
        Long taskId,
        String taskName,
        String status,
        String contents,
        String details,
        String outputPath,
        String errorMessage,
        Long fileSizeBytes,
        Long fileCount,
        LocalDateTime startedAt,
        LocalDateTime finishedAt) {

    public static BackupExecutionView from(BackupExecution execution) {
        return new BackupExecutionView(
                execution.getId(), execution.getTaskId(), execution.getTaskName(),
                execution.getStatus().name(), execution.getContents(), execution.getDetails(),
                execution.getOutputPath(), execution.getErrorMessage(),
                execution.getFileSizeBytes(), execution.getFileCount(),
                execution.getStartedAt(), execution.getFinishedAt());
    }
}
