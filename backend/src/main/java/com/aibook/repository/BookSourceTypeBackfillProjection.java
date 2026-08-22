package com.aibook.repository;

/** 历史入库方式回填所需的最小书籍字段。 */
public interface BookSourceTypeBackfillProjection {

    Long getId();

    String getFilePath();
}
