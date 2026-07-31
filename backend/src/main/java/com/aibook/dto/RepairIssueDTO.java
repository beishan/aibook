package com.aibook.dto;

import com.aibook.model.entity.RepairIssueStatus;
import com.aibook.model.entity.RepairIssueType;
import com.aibook.model.entity.RepairSource;
import com.aibook.model.entity.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 修复问题 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepairIssueDTO {

    private Long id;
    private Long taskId;
    private Integer chapterIndex;
    private String chapterTitle;
    private RepairIssueType type;
    private Integer startOffset;
    private Integer endOffset;
    private String originalText;
    private String suggestedText;
    private String reason;
    private String ruleId;
    private Double confidence;
    private RepairIssueStatus status;
    private RepairSource source;
    private RiskLevel riskLevel;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 候选结果（如编码修复时的多个候选） */
    private List<String> candidates;
}
