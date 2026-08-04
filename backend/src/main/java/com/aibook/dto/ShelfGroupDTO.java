package com.aibook.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ShelfGroupDTO {
    private Long id;
    private String name;
    private String description;
    private String icon;
    private String color;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private List<BookDTO> books;
}
