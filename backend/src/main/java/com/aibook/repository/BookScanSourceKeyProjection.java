package com.aibook.repository;

/** 已存在的书籍与扫描目录来源键。 */
public interface BookScanSourceKeyProjection {

    Long getBookId();

    Long getScanDirectoryId();
}
