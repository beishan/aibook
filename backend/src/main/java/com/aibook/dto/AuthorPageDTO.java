package com.aibook.dto;

import java.util.List;

/** 作者管理分页结果及全局统计。 */
public record AuthorPageDTO(
        List<AuthorDTO> content,
        long totalElements,
        int totalPages,
        int number,
        int size,
        AuthorSummaryDTO summary) {

    public record AuthorSummaryDTO(
            long totalAuthorCount,
            long activeAuthorCount,
            long relatedBookCount) { }
}
