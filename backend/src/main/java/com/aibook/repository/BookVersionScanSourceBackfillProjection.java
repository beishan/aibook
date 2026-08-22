package com.aibook.repository;

/** 回填扫描目录来源时，BookVersion 所需的最小字段。 */
public interface BookVersionScanSourceBackfillProjection {

    Long getId();

    Long getBookId();

    Long getUserId();

    String getFilePath();
}
