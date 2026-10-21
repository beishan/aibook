package com.aibook.repository;

import com.aibook.model.entity.OperationLogCleanupSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationLogCleanupScheduleRepository
        extends JpaRepository<OperationLogCleanupSchedule, Long> { }
