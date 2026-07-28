package com.aibook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 分类 DTO，避免直接序列化 JPA 父子关系。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDTO {

    private Long id;
    private String name;
    private String description;
    private Long parentId;
    private Integer sortOrder;
    private Boolean builtIn;
    private Boolean enabled;
    private Long directBookCount;
    private Long bookCount;
    @Builder.Default
    private List<CategoryDTO> children = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
