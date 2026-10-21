package com.aibook.repository;

import com.aibook.model.entity.OperationLog;
import com.aibook.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {
    Page<OperationLog> findByUser(User user, Pageable pageable);
    @Query("""
            select l from OperationLog l where l.user = :user
              and (:action is null or l.action = :action)
              and (:pattern = '' or lower(l.description) like :pattern escape '!'
                   or lower(coalesce(l.details, '')) like :pattern escape '!'
                   or lower(coalesce(l.bookTitle, '')) like :pattern escape '!')
            """)
    Page<OperationLog> search(@Param("user") User user,
            @Param("action") OperationLog.Action action, @Param("pattern") String pattern,
            Pageable pageable);

    @Modifying
    @Query("update OperationLog l set l.retained = true where l.id = :id and l.user = :user")
    int retain(@Param("user") User user, @Param("id") Long id);

    @Modifying
    @Query("delete from OperationLog l where l.id = :id and l.user = :user")
    int deleteOwned(@Param("user") User user, @Param("id") Long id);

    @Modifying
    @Query("""
            delete from OperationLog l where l.user = :user and l.action = :action
              and coalesce(l.retained, false) = false and l.createdAt < :cutoff
            """)
    int deleteExpired(@Param("user") User user, @Param("action") OperationLog.Action action,
            @Param("cutoff") LocalDateTime cutoff);

    boolean existsByUserAndRetainedTrue(User user);
}
