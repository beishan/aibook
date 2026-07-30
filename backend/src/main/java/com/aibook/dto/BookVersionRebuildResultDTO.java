package com.aibook.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BookVersionRebuildResultDTO {
    private int scannedBooks;
    private int rebuiltGroups;
    private int mergedBooks;
    private int aggregatedVersions;
    private int remainingBooks;
}
