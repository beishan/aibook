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
        String currentStage,
        Integer progressPercent,
        String progressDetail,
        String outputPath,
        String errorMessage,
        Long fileSizeBytes,
        Long fileCount,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String executionStatus,
        LocalDateTime deletedAt,
        String deletionReason) {

    public static BackupExecutionView from(BackupExecution execution) {
        return new BackupExecutionView(
                execution.getId(), execution.getTaskId(), execution.getTaskName(),
                execution.getDeletionReason() != null ? "DELETED" : execution.getStatus().name(),
                execution.getContents(), execution.getDetails(),
                execution.getCurrentStage(), execution.getProgressPercent(),
                execution.getProgressDetail(),
                execution.getOutputPath(), execution.getErrorMessage(),
                execution.getFileSizeBytes(), execution.getFileCount(),
                execution.getStartedAt(), execution.getFinishedAt(),
                execution.getStatus().name(), execution.getDeletedAt(), execution.getDeletionReason());
    }
}
