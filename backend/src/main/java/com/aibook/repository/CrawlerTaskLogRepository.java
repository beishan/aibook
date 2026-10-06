package com.aibook.repository;

import com.aibook.model.entity.CrawlerTaskLog;
import com.aibook.model.entity.User;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CrawlerTaskLogRepository extends JpaRepository<CrawlerTaskLog, Long> {
    @Query("""
            select l from CrawlerTaskLog l
            where l.user = :user and l.executorId = :executorId
              and (:failedOnly = false or l.failed = true)
            order by l.createdAt desc, l.id desc
            """)
    Page<CrawlerTaskLog> findExecutionLogs(@Param("user") User user,
            @Param("executorId") Long executorId, @Param("failedOnly") boolean failedOnly,
            Pageable pageable);
    List<CrawlerTaskLog> findByUserAndCrawlerBookIdOrderByCreatedAtDescIdDesc(
            User user, Long crawlerBookId, Pageable pageable);
}
