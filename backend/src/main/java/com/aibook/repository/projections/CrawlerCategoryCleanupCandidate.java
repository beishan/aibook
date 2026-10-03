package com.aibook.repository.projections;

public interface CrawlerCategoryCleanupCandidate {
    Long getId();
    String getBookName();
    String getCategory();
    String getSiteName();
    Long getLibraryBookId();
    Long getLibraryCategoryId();
    String getLibraryCategoryName();
}
