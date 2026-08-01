package com.aibook.dto;

import com.aibook.model.entity.MatchScope;
import com.aibook.model.entity.RepairAction;
import com.aibook.model.entity.RepairIssueType;
import com.aibook.model.entity.RiskLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建/更新修复规则请求
 */
@Data
public class CreateRuleRequest {

    @NotBlank
    private String name;

    @NotNull
    private RepairIssueType type;

    @NotBlank
    private String pattern;

    private MatchScope matchScope = MatchScope.LINE;

    private RepairAction action = RepairAction.DELETE_LINE;

    private String replacement;

    private RiskLevel riskLevel = RiskLevel.LOW;

    private Boolean enabled = true;

    private Boolean whitelist = false;

    private String scope = "ALL_BOOKS";

    private Long templateId;

    private Long bookId;
}
