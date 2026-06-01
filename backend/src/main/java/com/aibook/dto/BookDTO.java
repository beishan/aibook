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
    private String isbn;
    private String publisher;
    private String publishDate;
    private String description;
    private String coverUrl;
    private String format;
    private String filePath;
    private Long fileSize;
    private String language;
    private Integer rating;
    private String readingStatus;
    private String categoryName;
    private List<String> tagNames;
    private Boolean isFavorite;
    private Boolean isWanted;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
