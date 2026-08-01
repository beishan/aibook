package com.aibook.dto;

import com.aibook.model.entity.RepairMode;
import com.aibook.model.entity.RepairIssueStatus;
import com.aibook.model.entity.RepairIssueType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 修复任务 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepairTaskDTO {

    private Long id;
    private Long bookId;
    private String bookTitle;
    private Long versionId;
    private Long templateId;
    private RepairMode repairMode;
    private String status;
    private String originalContentVersion;
    private String repairedContentVersion;
    private String optionsJson;
    private String reportJson;
    private Integer totalIssueCount;
    private Integer detectedChapterCount;
    private Integer pendingIssueCount;
    private Integer acceptedIssueCount;
    private Integer rejectedIssueCount;
    private Integer ignoredIssueCount;
    private Integer appliedIssueCount;
    private Long userId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 各问题类型数量统计 */
    private Map<RepairIssueType, Integer> issueTypeCounts;

    /** 各状态数量统计 */
    private Map<RepairIssueStatus, Integer> issueStatusCounts;
}
