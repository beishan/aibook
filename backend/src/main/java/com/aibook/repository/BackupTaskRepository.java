package com.aibook.repository;

import com.aibook.model.entity.BackupTask;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackupTaskRepository extends JpaRepository<BackupTask, Long> {
    List<BackupTask> findByEnabledTrueAndScheduleEnabledTrue();
}
