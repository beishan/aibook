package com.aibook.repository.projections;

public interface CrawlerCategoryCleanupCandidate {
    Long getId();
    String getBookName();
    String getAuthor();
    String getCategory();
    String getSiteName();
    Long getLibraryBookId();
    Long getLibraryCategoryId();
    String getLibraryCategoryName();
}
