package com.aibook.dto;

import java.util.List;

public record BookSimilarityScanDTO(
        String taskId,
        String status,
        String message,
        int totalBooks,
        int processedBooks,
        int matchedBooks,
        int totalGroups,
        int page,
        int size,
        List<Group> groups) {
    public record Group(String id, int similarity, List<SimilarBookDTO> books) {}
}
