package com.aibook.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BookVersionRebuildTaskDTO {
    private String taskId;
    private String status;
    private String message;
    private int totalBooks;
    private int processedBooks;
    private int matchedGroups;
    private int completedGroups;
    private int mergedBooks;
    private int aggregatedVersions;
    private int skippedBooks;
    private int failedBooks;
    private String currentBookTitle;
    private long startedAt;
    private long finishedAt;
    private long elapsedMs;
    private List<String> errors;
}
