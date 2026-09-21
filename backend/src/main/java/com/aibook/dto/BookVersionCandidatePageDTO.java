package com.aibook.dto;

import java.util.List;

/** 书籍版本导入候选分页。 */
public record BookVersionCandidatePageDTO(
        List<BookVersionCandidateDTO> content,
        long totalElements,
        int totalPages,
        int number,
        int size) { }
