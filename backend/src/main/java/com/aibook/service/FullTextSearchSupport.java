package com.aibook.service;

import org.springframework.stereotype.Component;

/**
 * 全文检索能力状态。
 *
 * <p>由启动初始化器根据 PostgreSQL 是否可用 zhparser 扩展设置；
 * 未启用时书籍搜索自动降级为 LIKE + 拼音匹配，保证功能不中断。</p>
 */
@Component
public class FullTextSearchSupport {

    private volatile boolean fullTextEnabled = false;

    /** zhparser 中文全文检索是否可用。 */
    public boolean isFullTextEnabled() {
        return fullTextEnabled;
    }

    /** 由启动初始化器在 zhparser 就绪后调用启用。 */
    public void enableFullText() {
        this.fullTextEnabled = true;
    }
}
