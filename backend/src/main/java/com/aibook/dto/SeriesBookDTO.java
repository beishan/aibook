package com.aibook.dto;

import java.math.BigDecimal;

/** 系列页仅返回必要展示字段，不读取标签、章节或文件路径。 */
public record SeriesBookDTO(Long id, String title, String author, String coverUrl,
        String format, BigDecimal seriesIndex, String readingStatus) { }
