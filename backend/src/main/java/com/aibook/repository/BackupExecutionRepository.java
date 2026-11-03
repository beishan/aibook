package com.aibook.repository;

import com.aibook.model.entity.BackupExecution;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BackupExecutionRepository extends JpaRepository<BackupExecution, Long> {
    boolean existsByStatusIn(List<BackupExecution.Status> statuses);

    @Query(value = "select e from BackupExecution e order by case when e.deletionReason is null then 0 else 1 end, e.startedAt desc, e.id desc",
            countQuery = "select count(e) from BackupExecution e")
    Page<BackupExecution> findOrderedExecutions(Pageable pageable);
}
