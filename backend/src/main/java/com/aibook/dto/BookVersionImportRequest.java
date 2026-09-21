package com.aibook.dto;

import java.util.List;

/** 从已有书籍导入版本的选择及源书籍处理方式。 */
public record BookVersionImportRequest(
        List<SourceSelection> sources,
        SourceHandling sourceHandling) {

    public enum SourceHandling {
        KEEP,
        MERGE
    }

    public record SourceSelection(Long bookId, List<Long> versionIds) { }
}
