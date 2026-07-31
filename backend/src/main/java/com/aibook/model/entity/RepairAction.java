package com.aibook.model.entity;

/**
 * 修复规则处理方式
 */
public enum RepairAction {
    /** 删除匹配内容 */
    DELETE_MATCH,
    /** 删除整行 */
    DELETE_LINE,
    /** 删除整个段落 */
    DELETE_PARAGRAPH,
    /** 仅标记 */
    MARK_ONLY,
    /** 替换为指定内容 */
    REPLACE
}
