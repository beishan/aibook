package com.aibook.model.entity;

/**
 * 修复问题类型
 */
public enum RepairIssueType {
    /** 编码乱码 */
    ENCODING,
    /** 广告信息 */
    AD,
    /** 章节标题识别 */
    CHAPTER,
    /** 章节编号异常（缺失、重复、乱序） */
    CHAPTER_ANOMALY,
    /** 段落格式（换行、空行、缩进） */
    PARAGRAPH,
    /** 标点与空格 */
    PUNCTUATION,
    /** 重复内容（章节、段落） */
    DUPLICATE,
    /** 不可见字符 */
    INVISIBLE_CHAR,
    /** 章节粘连 */
    CHAPTER_ADHESION,
    /** 繁简转换 */
    TRADITIONAL_SIMPLIFIED,
    /** AI 辅助建议 */
    AI_SUGGESTION
}
