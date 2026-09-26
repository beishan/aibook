package com.aibook.repository.projections;

/** A book record matched by its normalized title. */
public interface BookTitleMatchProjection {
    String getNormalizedTitle();

    Long getRecordId();
}
