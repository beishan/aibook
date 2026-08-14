package com.aibook.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class BookConversionTaskDTO {
    private Long id;
    private Long sourceBookId;
    private Long sourceVersionId;
    private String sourceFilename;
    private String sourceFormat;
    private String targetFormat;
    private String status;
    private String stage;
    private Integer progress;
    private String errorMessage;
    private String title;
    private String author;
    private String description;
    private String isbn;
    private String publisher;
    private String publishDate;
    private String language;
    private String categoryName;
    private List<String> tags;
    private String seriesName;
    private String seriesIndex;
    private String coverUrl;
    private String encoding;
    private String newlineFormat;
    private Long sourceSize;
    private Long characterCount;
    private Integer anomalyCount;
    private List<ConversionChapterDTO> chapters;
    private BookConversionUpdateRequest settings;
    private String outputFilename;
    private Long outputSize;
    private Long elapsedMillis;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
