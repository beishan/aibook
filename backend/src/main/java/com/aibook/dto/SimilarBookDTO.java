package com.aibook.dto;

import com.aibook.model.entity.Book;

public record SimilarBookDTO(
        Long id, String title, String author, String format, Book.SourceType sourceType,
        String sourceSiteName, String sourcePath, Integer chapterCount, Long fileSize) {
    public SimilarBookDTO(Long id, String title, String author, String format) {
        this(id, title, author, format, null, null, null, null, null);
    }
}
