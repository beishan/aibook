package com.aibook.dto;

import com.aibook.model.entity.RepairIssueStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 批量更新修复问题请求
 */
@Data
public class BatchUpdateIssuesRequest {

    @NotNull
    private List<Long> issueIds;

    @NotNull
    private RepairIssueStatus status;
}
