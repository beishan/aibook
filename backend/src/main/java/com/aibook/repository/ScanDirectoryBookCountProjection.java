package com.aibook.repository;

/** 扫描目录关联的有效逻辑书籍数。 */
public interface ScanDirectoryBookCountProjection {

    Long getScanDirectoryId();

    long getBookCount();
}
