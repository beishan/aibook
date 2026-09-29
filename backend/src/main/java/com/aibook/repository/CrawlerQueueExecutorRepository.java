package com.aibook.repository;

import com.aibook.model.entity.CrawlerQueueExecutor;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrawlerQueueExecutorRepository extends JpaRepository<CrawlerQueueExecutor, Long> {
    List<CrawlerQueueExecutor> findByQueueIdOrderBySortOrderAscIdAsc(Long queueId);
}
