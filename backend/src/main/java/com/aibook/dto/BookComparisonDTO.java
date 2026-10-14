package com.aibook.dto;

import java.util.List;

public record BookComparisonDTO(
        BookDTO book, Long versionId, String format, Long fileSize,
        boolean contentSupported, String message, List<BookTocItemDTO> chapters) {
    public record Chapter(int index, String title, String content, int totalCharacters, boolean truncated) {}
}
