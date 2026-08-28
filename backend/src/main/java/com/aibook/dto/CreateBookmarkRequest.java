package com.aibook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建书签请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookmarkRequest {

    private String title;

    private String excerpt;

    private String chapter;

    private Integer chapterIndex;

    private String cfi;

    private Long scrollPosition;

    private Integer page;
}
