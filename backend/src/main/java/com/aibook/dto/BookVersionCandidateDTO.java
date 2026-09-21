package com.aibook.dto;

/** 可向当前书籍导入版本的书库候选项。 */
public record BookVersionCandidateDTO(
        Long id,
        String title,
        String author,
        String coverUrl,
        String format,
        long versionCount,
        boolean sameTitle) { }
