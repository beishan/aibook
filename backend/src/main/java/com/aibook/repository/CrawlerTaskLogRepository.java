package com.aibook.repository;

import com.aibook.model.entity.CrawlerTaskLog;
import com.aibook.model.entity.User;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import java.util.Collection;
import java.time.LocalDateTime;
import org.springframework.data.repository.query.Param;

public interface CrawlerTaskLogRepository extends JpaRepository<CrawlerTaskLog, Long> {
    @Query("""
            select l from CrawlerTaskLog l
            where l.user = :user
              and (:pattern = '' or lower(l.description) like :pattern escape '!'
                   or lower(coalesce(l.details, '')) like :pattern escape '!'
                   or lower(coalesce(l.bookName, '')) like :pattern escape '!')
              and (:type = '' or locate(concat('任务ID：', l.taskId, '；任务类型：', :type, '；网站：'),
                                      coalesce(l.details, '')) = 1)
            order by l.createdAt desc, l.id desc
            """)
    Page<CrawlerTaskLog> searchForManagement(@Param("user") User user, @Param("pattern") String pattern,
            @Param("type") String type, Pageable pageable);

    @Modifying
    @Query("update CrawlerTaskLog l set l.retained = :retained where l.user = :user and l.id in :ids")
    int setRetained(@Param("user") User user, @Param("ids") Collection<Long> ids,
            @Param("retained") boolean retained);

    @Modifying
    @Query("delete from CrawlerTaskLog l where l.user = :user and l.id in :ids")
    int deleteSelected(@Param("user") User user, @Param("ids") Collection<Long> ids);

    @Modifying
    @Query("""
            delete from CrawlerTaskLog l where l.user.id = :userId
              and coalesce(l.retained, false) = false and l.createdAt < :cutoff
              and locate(concat('任务ID：', l.taskId, '；任务类型：', :type, '；网站：'),
                         coalesce(l.details, '')) = 1
            """)
    int deleteExpired(@Param("userId") Long userId, @Param("type") String type,
            @Param("cutoff") LocalDateTime cutoff);

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
