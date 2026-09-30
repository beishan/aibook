package com.aibook.repository;

import com.aibook.model.entity.RewriteProject;
import com.aibook.model.entity.RewriteSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RewriteSnapshotRepository extends JpaRepository<RewriteSnapshot, Long> {
    List<RewriteSnapshot> findByProjectOrderBySnapshotNumberDesc(RewriteProject project);

    List<RewriteSnapshot> findByProjectAndAutomaticTrueOrderBySnapshotNumberDesc(
            RewriteProject project);

    Optional<RewriteSnapshot> findTopByProjectOrderBySnapshotNumberDesc(RewriteProject project);

    Optional<RewriteSnapshot> findByIdAndProject(Long id, RewriteProject project);

    long countByProject(RewriteProject project);

    long countByProjectAndAutomaticTrue(RewriteProject project);

    long countByProjectAndAutomaticFalse(RewriteProject project);

    @Query(value = "SELECT COALESCE(SUM(OCTET_LENGTH(snapshot_content)), 0) "
            + "FROM rewrite_snapshots WHERE project_id = :projectId", nativeQuery = true)
    long totalStorageBytes(@Param("projectId") Long projectId);
}
