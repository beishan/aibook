package com.aibook.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ReadingProgressDTO {
    private Long id;
    private Long bookId;
    private Long versionId;
    private String currentChapter;
    private String currentChapterTitle;
    private Integer chapterProgress;
    private Integer totalProgress;
    private Long readingTimeSeconds;
    private LocalDateTime lastReadAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
