package com.aibook.repository;

import com.aibook.model.entity.CrawlerMihomoReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrawlerMihomoReceiptRepository extends JpaRepository<CrawlerMihomoReceipt, Long> {
    boolean existsByExecutorIdAndEventKey(Long executorId, String eventKey);
    void deleteByExecutorId(Long executorId);
}
