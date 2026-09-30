package com.aibook.repository;

import com.aibook.model.entity.RewriteMemo;
import com.aibook.model.entity.RewriteMemo.Type;
import com.aibook.model.entity.RewriteMemo.State;
import com.aibook.model.entity.RewriteProject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RewriteMemoRepository extends JpaRepository<RewriteMemo, Long> {
    List<RewriteMemo> findByProjectAndTypeOrderByUpdatedAtDesc(RewriteProject project, Type type);

    List<RewriteMemo> findByProjectOrderByUpdatedAtDesc(RewriteProject project);

    List<RewriteMemo> findByProjectAndTypeAndStateAndChapterIsNotNull(
            RewriteProject project, Type type, State state);

    Optional<RewriteMemo> findByIdAndProject(Long id, RewriteProject project);

    void deleteByProject(RewriteProject project);
}
