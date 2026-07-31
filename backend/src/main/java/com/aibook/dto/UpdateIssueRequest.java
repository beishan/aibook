package com.aibook.dto;

import com.aibook.model.entity.RepairIssueStatus;
import com.aibook.model.entity.RepairIssueType;
import com.aibook.model.entity.RepairSource;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 更新单个修复问题请求
 */
@Data
public class UpdateIssueRequest {

    @NotNull
    private RepairIssueStatus status;

    /** 手动修改后的文本（可选） */
    private String manualText;

    /** 操作来源 */
    private RepairSource source;

    /** 是否应用到全部相同问题 */
    private Boolean applyToAll = false;

    /** 应用范围：ALL / CHAPTER / BOOK */
    private String applyScope = "ALL";
}
