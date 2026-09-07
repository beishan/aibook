package com.aibook.repository;

import com.aibook.model.entity.CrawlerTaskLog;
import com.aibook.model.entity.User;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrawlerTaskLogRepository extends JpaRepository<CrawlerTaskLog, Long> {
    List<CrawlerTaskLog> findByUserAndCrawlerBookIdOrderByCreatedAtDescIdDesc(
            User user, Long crawlerBookId, Pageable pageable);
}
