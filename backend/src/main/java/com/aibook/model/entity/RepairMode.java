package com.aibook.model.entity;

/**
 * TXT 内容修复模式
 */
public enum RepairMode {
    /** 安全修复：仅处理低风险问题 */
    SAFE,
    /** 标准修复：安全修复 + 常见广告清理、章节统一等 */
    STANDARD,
    /** 深度修复：标准修复 + 模糊广告识别、章节粘连检测等 */
    DEEP
}
