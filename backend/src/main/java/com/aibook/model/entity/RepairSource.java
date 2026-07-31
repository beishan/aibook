package com.aibook.model.entity;

/**
 * 修复操作来源
 */
public enum RepairSource {
    /** 系统自动检测 */
    AUTO,
    /** 用户手动修改 */
    MANUAL,
    /** 用户批量应用 */
    BATCH
}
