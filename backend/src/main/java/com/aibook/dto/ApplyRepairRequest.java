package com.aibook.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 应用修复请求（执行修复并保存新版本）
 */
@Data
public class ApplyRepairRequest {

    @NotNull
    private Long taskId;

    /** 是否只应用已接受的问题 */
    private Boolean acceptedOnly = true;
}
