package com.aibook.repository;

import com.aibook.model.entity.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface CrawlerTaskRepository extends JpaRepository<CrawlerTask, String> {
    Optional<CrawlerTask> findByIdAndUser(String id, User user);
    @Query("""
            select t from CrawlerTask t
            where t.user = :user
            order by case when t.status = :runningStatus then 0 else 1 end, t.createdAt desc
            """)
    Page<CrawlerTask> findByUserRunningFirst(
            @Param("user") User user,
            @Param("runningStatus") CrawlerTask.TaskStatus runningStatus,
            Pageable pageable);
    Page<CrawlerTask> findByUserAndStatusInOrderByCreatedAtDesc(
            User user, Collection<CrawlerTask.TaskStatus> statuses, Pageable pageable);
    List<CrawlerTask> findByStatusIn(Collection<CrawlerTask.TaskStatus> statuses);
    long countByUserAndStatus(User user, CrawlerTask.TaskStatus status);
    boolean existsBySiteAndTypeAndStatusIn(CrawlerSite site, CrawlerTask.TaskType type, Collection<CrawlerTask.TaskStatus> statuses);
    boolean existsByDiscoveryPageIdAndStatusIn(Long discoveryPageId, Collection<CrawlerTask.TaskStatus> statuses);
    boolean existsByCrawlerBookAndStatusIn(CrawlerBook book, Collection<CrawlerTask.TaskStatus> statuses);
    Optional<CrawlerTask> findFirstByCrawlerBookAndStatusOrderByUpdatedAtDesc(
            CrawlerBook book, CrawlerTask.TaskStatus status);
}
