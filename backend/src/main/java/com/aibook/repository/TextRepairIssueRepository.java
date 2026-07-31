package com.aibook.repository;

import com.aibook.model.entity.RepairIssueStatus;
import com.aibook.model.entity.RepairIssueType;
import com.aibook.model.entity.TextRepairIssue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TextRepairIssueRepository extends JpaRepository<TextRepairIssue, Long> {

    Page<TextRepairIssue> findByTaskIdOrderByChapterIndexAscStartOffsetAsc(
            Long taskId, Pageable pageable);

    List<TextRepairIssue> findByTaskIdOrderByChapterIndexAscStartOffsetAsc(Long taskId);

    List<TextRepairIssue> findByTaskIdAndType(
            Long taskId, RepairIssueType type);

    List<TextRepairIssue> findByTaskIdAndStatus(
            Long taskId, RepairIssueStatus status);

    List<TextRepairIssue> findByTaskIdAndStatusIn(
            Long taskId, List<RepairIssueStatus> statuses);

    long countByTaskIdAndStatus(Long taskId, RepairIssueStatus status);

    long countByTaskId(Long taskId);
}
