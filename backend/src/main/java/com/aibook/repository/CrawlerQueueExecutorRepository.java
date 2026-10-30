package com.aibook.repository;

import com.aibook.model.entity.CrawlerQueueExecutor;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrawlerQueueExecutorRepository extends JpaRepository<CrawlerQueueExecutor, Long> {
    List<CrawlerQueueExecutor> findByDeletionPendingTrue();
    @org.springframework.data.jpa.repository.Query("""
            select distinct e from CrawlerQueueExecutor e left join e.sharedQueueIds sharedQueue
            where e.queue.id = :queueId or sharedQueue = :queueId
            order by e.sortOrder, e.id
            """)
    List<CrawlerQueueExecutor> findByQueueIdOrderBySortOrderAscIdAsc(
            @org.springframework.data.repository.query.Param("queueId") Long queueId);
}
