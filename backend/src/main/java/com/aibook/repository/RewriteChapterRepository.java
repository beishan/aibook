package com.aibook.repository;

import com.aibook.model.entity.RewriteChapter;
import com.aibook.model.entity.RewriteProject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RewriteChapterRepository extends JpaRepository<RewriteChapter, Long> {
    List<RewriteChapter> findByProjectAndDeletedFalseOrderBySortIndexAsc(RewriteProject project);

    List<RewriteChapter> findByProjectAndDeletedTrueOrderBySortIndexAsc(RewriteProject project);

    List<RewriteChapter> findByProject(RewriteProject project);

    Optional<RewriteChapter> findByIdAndProjectAndDeletedFalse(Long id, RewriteProject project);

    long countByProjectAndDeletedFalse(RewriteProject project);
}
