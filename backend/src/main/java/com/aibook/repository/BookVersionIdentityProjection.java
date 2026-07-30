package com.aibook.repository;

/**
 * 多版本重建使用的轻量投影，刻意排除 description、chapterInfo 等大文本字段。
 */
public interface BookVersionIdentityProjection {
    Long getId();

    String getTitle();

    String getAuthor();

    String getIsbn();

    String getFilePath();
}
