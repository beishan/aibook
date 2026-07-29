package com.aibook.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScanRecordDTO {
    private Long id;
    private String taskId;
    private Long directoryId;
    private String directoryPath;
    private String status;
    private String message;
    private Integer totalCount;
    private Integer scannedCount;
    private Integer newBooks;
    private Integer skippedBooks;
    private Integer failedBooks;
    private Integer threadCount;
    private Long durationMs;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorDetails;
}
