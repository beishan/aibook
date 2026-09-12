package com.aibook.repository;

import com.aibook.model.entity.CrawlerScanResult;
import com.aibook.model.entity.CrawlerTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface CrawlerScanResultRepository extends JpaRepository<CrawlerScanResult, Long> {
    Page<CrawlerScanResult> findByTaskOrderByIdAsc(CrawlerTask task, Pageable pageable);
    @Transactional
    void deleteByTask(CrawlerTask task);
}
