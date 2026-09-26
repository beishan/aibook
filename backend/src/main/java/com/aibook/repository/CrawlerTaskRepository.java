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
    @Query(value = """
            select t from CrawlerTask t
            where t.user = :user
            order by case when t.status = :runningStatus then 0 else 1 end, t.createdAt desc
            """, countQuery = """
            select count(t) from CrawlerTask t
            where t.user = :user
              and (t.status = :runningStatus or t.status <> :runningStatus or t.status is null)
            """)
    Page<CrawlerTask> findByUserRunningFirst(
            @Param("user") User user,
            @Param("runningStatus") CrawlerTask.TaskStatus runningStatus,
            Pageable pageable);
    @Query(value = """
            select t from CrawlerTask t
            where t.user = :user and t.type = :type
            order by case when t.status = :runningStatus then 0 else 1 end, t.createdAt desc
            """, countQuery = """
            select count(t) from CrawlerTask t
            where t.user = :user and t.type = :type
              and (t.status = :runningStatus or t.status <> :runningStatus or t.status is null)
            """)
    Page<CrawlerTask> findByUserAndTypeRunningFirst(
            @Param("user") User user,
            @Param("type") CrawlerTask.TaskType type,
            @Param("runningStatus") CrawlerTask.TaskStatus runningStatus,
            Pageable pageable);
    Page<CrawlerTask> findByUserAndStatusInOrderByCreatedAtDesc(
            User user, Collection<CrawlerTask.TaskStatus> statuses, Pageable pageable);
    Page<CrawlerTask> findByUserAndTypeAndStatusInOrderByCreatedAtDesc(
            User user, CrawlerTask.TaskType type,
            Collection<CrawlerTask.TaskStatus> statuses, Pageable pageable);
    @Query(value = """
            select t from CrawlerTask t
            where t.user = :user
              and t.crawlerBook.favorite = true
              and (:type is null or t.type = :type)
              and (:status is null or t.status = :status)
              and (:failedOnly = false or t.status in :failedStatuses)
            order by case when t.status = :runningStatus then 0 else 1 end, t.createdAt desc
            """, countQuery = """
            select count(t) from CrawlerTask t
            where t.user = :user
              and t.crawlerBook.favorite = true
              and (:type is null or t.type = :type)
              and (:status is null or t.status = :status)
              and (:failedOnly = false or t.status in :failedStatuses)
              and (t.status = :runningStatus or t.status <> :runningStatus or t.status is null)
            """)
    Page<CrawlerTask> findFavoriteTasks(
            @Param("user") User user,
            @Param("type") CrawlerTask.TaskType type,
            @Param("status") CrawlerTask.TaskStatus status,
            @Param("failedOnly") boolean failedOnly,
            @Param("failedStatuses") Collection<CrawlerTask.TaskStatus> failedStatuses,
            @Param("runningStatus") CrawlerTask.TaskStatus runningStatus,
            Pageable pageable);
    List<CrawlerTask> findByUserAndStatusInOrderByCreatedAtDesc(
            User user, Collection<CrawlerTask.TaskStatus> statuses);
    List<CrawlerTask> findByStatusOrderByQueueOrderAsc(CrawlerTask.TaskStatus status);
    List<CrawlerTask> findByStatusIn(Collection<CrawlerTask.TaskStatus> statuses);
    long countByUserAndStatus(User user, CrawlerTask.TaskStatus status);
    boolean existsBySiteAndTypeAndStatusIn(CrawlerSite site, CrawlerTask.TaskType type, Collection<CrawlerTask.TaskStatus> statuses);
    boolean existsByDiscoveryPageIdAndStatusIn(Long discoveryPageId, Collection<CrawlerTask.TaskStatus> statuses);
    boolean existsByCrawlerBookAndStatusIn(CrawlerBook book, Collection<CrawlerTask.TaskStatus> statuses);
    Optional<CrawlerTask> findFirstByCrawlerBookAndStatusOrderByUpdatedAtDesc(
            CrawlerBook book, CrawlerTask.TaskStatus status);
    @Query("""
            select cast(t.finishedAt as LocalDate), t.status, count(t)
            from CrawlerTask t
            where t.user = :user and t.finishedAt >= :start and t.status in :statuses
            group by cast(t.finishedAt as LocalDate), t.status
            order by cast(t.finishedAt as LocalDate)
            """)
    List<Object[]> countFinishedByDayAndStatus(@Param("user") User user,
            @Param("start") java.time.LocalDateTime start,
            @Param("statuses") Collection<CrawlerTask.TaskStatus> statuses);
    @Query("select count(distinct t.crawlerBook.id) from CrawlerTask t "
            + "where t.user = :user and t.crawlerBook is not null")
    long countDistinctTaskedBooks(@Param("user") User user);
}
