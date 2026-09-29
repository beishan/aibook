package com.aibook.repository;

import com.aibook.model.entity.CrawlerQueueExecutorProxy;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrawlerQueueExecutorProxyRepository
        extends JpaRepository<CrawlerQueueExecutorProxy, Long> {
    List<CrawlerQueueExecutorProxy> findByExecutorIdOrderBySortOrderAscIdAsc(Long executorId);

    void deleteByExecutorId(Long executorId);
}
