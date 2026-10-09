package com.aibook.repository;

import com.aibook.model.entity.BackupExecution;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackupExecutionRepository extends JpaRepository<BackupExecution, Long> {
    boolean existsByStatusIn(List<BackupExecution.Status> statuses);

    List<BackupExecution> findTop100ByOrderByStartedAtDesc();
}
