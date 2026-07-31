package com.aibook.dto;

import com.aibook.model.entity.RepairMode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建修复任务请求
 */
@Data
public class CreateRepairTaskRequest {

    @NotNull
    private Long bookId;

    /** 书籍版本 ID，为空时使用主版本 */
    private Long versionId;

    @NotNull
    private RepairMode repairMode;

    /** 修复模板 ID，可选 */
    private Long templateId;

    /** 自定义配置 JSON，覆盖模板设置 */
    private String optionsJson;
}
