package com.aibook.repository;

import com.aibook.model.entity.OperationLogRetentionPolicy;
import com.aibook.model.entity.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OperationLogRetentionPolicyRepository
        extends JpaRepository<OperationLogRetentionPolicy, Long> {
    List<OperationLogRetentionPolicy> findByUser(User user);

    @Query("select distinct p.user.id from OperationLogRetentionPolicy p where p.retentionDays > 0")
    List<Long> findExpiringPolicyUserIds();
}
