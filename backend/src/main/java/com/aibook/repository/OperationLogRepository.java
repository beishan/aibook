package com.aibook.repository;

import com.aibook.model.entity.OperationLog;
import com.aibook.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {
    Page<OperationLog> findByUser(User user, Pageable pageable);
}
