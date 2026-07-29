package com.aibook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 书籍标签 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagDTO {

    private Long id;
    private String name;
    private String color;
    private Long bookCount;
    private LocalDateTime createdAt;
}
