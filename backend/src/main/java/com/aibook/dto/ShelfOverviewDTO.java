package com.aibook.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ShelfOverviewDTO {
    private List<BookDTO> ungroupedBooks;
    private List<ShelfGroupDTO> groups;
    private int totalBooks;
}
