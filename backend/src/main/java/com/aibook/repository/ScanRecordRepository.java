package com.aibook.repository;

import com.aibook.model.entity.ScanRecord;
import com.aibook.model.entity.User;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ScanRecordRepository extends JpaRepository<ScanRecord, Long> {

    @Query("""
            SELECT r FROM ScanRecord r
            WHERE r.user = :user
              AND (:directoryId IS NULL OR r.directoryId = :directoryId)
              AND (:status IS NULL OR r.status = :status)
            """)
    Page<ScanRecord> findHistory(
            @Param("user") User user,
            @Param("directoryId") Long directoryId,
            @Param("status") ScanRecord.Status status,
            Pageable pageable);

    List<ScanRecord> findByStatusIn(Collection<ScanRecord.Status> statuses);
}
