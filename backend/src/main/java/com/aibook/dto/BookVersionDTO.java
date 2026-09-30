package com.aibook.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class BookVersionDTO {
    private Long id;
    private String displayName;
    private String format;
    private Long fileSize;
    private String fileHash;
    private Boolean primaryVersion;
    private Integer chapterCount;
    private Long rewriteProjectId;
    private String rewriteStatus;
    private Long sourceVersionId;
    private LocalDateTime createdAt;
}
