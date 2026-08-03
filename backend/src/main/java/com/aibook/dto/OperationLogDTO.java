package com.aibook.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OperationLogDTO {
    private Long id;
    private String action;
    private Long bookId;
    private String bookTitle;
    private String description;
    private String details;
    private LocalDateTime createdAt;
}
