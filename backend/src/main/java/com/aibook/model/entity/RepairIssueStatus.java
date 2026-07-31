package com.aibook.model.entity;

/**
 * 修复问题状态
 */
public enum RepairIssueStatus {
    /** 待处理 */
    PENDING,
    /** 已接受修复 */
    ACCEPTED,
    /** 已拒绝修复 */
    REJECTED,
    /** 已忽略 */
    IGNORED,
    /** 已应用 */
    APPLIED,
    /** 已撤销 */
    REVERTED
}
