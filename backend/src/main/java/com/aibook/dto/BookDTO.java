package com.aibook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 书籍 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookDTO {

    private Long id;
    private String title;
    private String author;
    private String seriesName;
    private java.math.BigDecimal seriesIndex;
    private String isbn;
    private String publisher;
    private String publishDate;
    private String description;
    private String coverUrl;
    private String format;
    private String filePath;
    private String sourceType;
    private String sourcePath;
    private Long fileSize;
    private String language;
    private Integer rating;
    private String readingStatus;
    private Long categoryId;
    private String categoryName;
    private String categoryPath;
    private List<TagDTO> tags;
    private List<String> tagNames;
    private Boolean isFavorite;
    private Boolean isWanted;
    private Boolean onShelf;
    private Long shelfGroupId;
    private LocalDateTime shelfAddedAt;
    private Integer shelfSortOrder;
    private String notes;
    private String chapterInfo;
    private Integer chapterCount;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
