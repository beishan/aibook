package com.aibook.repository;

import com.aibook.model.entity.RewriteChapter;
import com.aibook.model.entity.RewriteRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RewriteRevisionRepository extends JpaRepository<RewriteRevision, Long> {
    List<RewriteRevision> findByChapterOrderByRevisionNumberDesc(RewriteChapter chapter);

    Optional<RewriteRevision> findByChapterAndRevisionNumber(
            RewriteChapter chapter, Long revisionNumber);

    void deleteByChapter(RewriteChapter chapter);
}
