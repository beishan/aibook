package com.aibook.repository;

/** 历史扫描来源回填所需的最小书籍字段，避免把整本书的长文本加载进内存。 */
public interface BookScanSourceBackfillProjection {

    Long getId();

    Long getUserId();

    String getFilePath();
}
