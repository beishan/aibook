package com.aibook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量刮削任务状态 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrapeTaskDTO {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 任务状态：PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
     */
    private String status;

    /**
     * 总书籍数
     */
    private int totalBooks;

    /**
     * 已完成数
     */
    private int completedBooks;

    /**
     * 失败数
     */
    private int failedBooks;

    /**
     * 当前正在刮削的书名
     */
    private String currentBookTitle;

    /**
     * 每本书的刮削结果
     */
    @Builder.Default
    private List<BookScrapeResult> results = new ArrayList<>();

    /**
     * 开始时间戳
     */
    private long startTime;

    /**
     * 结束时间戳
     */
    private long endTime;

    /**
     * 错误信息（任务级别）
     */
    private String errorMessage;

    /**
     * 单本书的刮削结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookScrapeResult {
        private Long bookId;
        private String title;
        private boolean success;
        private List<String> updatedFields;
        private String error;

        @Builder.Default
        private List<String> updatedFieldNames = new ArrayList<>();
    }
}
