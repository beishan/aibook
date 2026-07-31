package com.aibook.model.entity;

/**
 * 广告规则匹配范围
 */
public enum MatchScope {
    /** 当前内容 */
    CONTENT,
    /** 当前行 */
    LINE,
    /** 当前段落 */
    PARAGRAPH,
    /** 章节开头 */
    CHAPTER_START,
    /** 章节结尾 */
    CHAPTER_END
}
