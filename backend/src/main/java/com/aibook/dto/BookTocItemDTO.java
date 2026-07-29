package com.aibook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 书籍目录项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookTocItemDTO {

    private Integer index;
    private String title;
    private String href;
    private Integer startIndex;
    private Integer endIndex;
    private Integer depth;
}
