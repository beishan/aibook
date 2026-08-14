package com.aibook.dto;

import lombok.Data;
import java.util.List;

@Data
public class BookConversionUpdateRequest {
    private String title;
    private String author;
    private String description;
    private String isbn;
    private String publisher;
    private String publishDate;
    private String language;
    private String categoryName;
    private List<String> tags;
    private String seriesName;
    private String seriesIndex;
    private String outputFilename;
    private String chapterPattern;
    private String epubVersion;
    private String firstLineIndent;
    private String paragraphSpacing;
    private Double lineHeight;
    private Boolean removeExtraBlankLines;
    private Boolean trimLineEnd;
    private Boolean normalizeWidth;
    private List<ConversionChapterDTO> chapters;
}
