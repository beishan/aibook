package com.aibook.dto;

import java.util.List;

/** 单页书库标签扫描结果与进度。 */
public record TagLibraryScanPageDTO(
        List<String> tags,
        long scannedBooks,
        long totalBooks,
        boolean complete) {}
