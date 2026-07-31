package com.aibook.dto;

import com.aibook.model.entity.MatchScope;
import com.aibook.model.entity.RepairAction;
import com.aibook.model.entity.RepairIssueType;
import com.aibook.model.entity.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 修复规则 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepairRuleDTO {

    private Long id;
    private String name;
    private RepairIssueType type;
    private String pattern;
    private MatchScope matchScope;
    private RepairAction action;
    private String replacement;
    private RiskLevel riskLevel;
    private Boolean enabled;
    private Boolean systemRule;
    private String scope;
    private Long templateId;
    private Long userId;
}
