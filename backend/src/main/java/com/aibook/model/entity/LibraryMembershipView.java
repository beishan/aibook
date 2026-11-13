package com.aibook.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;

/** 只读馆藏投影，用于分页前排除已有馆藏，不参与 JDBC 管理表的建表。 */
@Entity
@Immutable
@Subselect("SELECT library_id || ':' || book_id AS id, library_id, book_id FROM personal_library_items")
public class LibraryMembershipView {

    @Id
    private String id;

    @Column(name = "library_id")
    private Long libraryId;

    @Column(name = "book_id")
    private Long bookId;
}
