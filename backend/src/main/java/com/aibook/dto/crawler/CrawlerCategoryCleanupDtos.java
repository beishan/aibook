package com.aibook.dto.crawler;

import jakarta.validation.constraints.Size;
import java.util.List;

public final class CrawlerCategoryCleanupDtos {
    private CrawlerCategoryCleanupDtos() { }

    public record Request(Long siteId, @Size(max = 100) List<@Size(max = 255) String> categoryNames,
            boolean syncLibrary) { }

    public record Item(Long id, String bookName, String category, String siteName,
            boolean matchingLibraryCategory) { }

    public record Preview(int matchedBooks, int matchingLibraryBooks, List<Item> samples) { }

    public record Result(int clearedBooks, int clearedLibraryBooks, int skippedBooks) { }
}
