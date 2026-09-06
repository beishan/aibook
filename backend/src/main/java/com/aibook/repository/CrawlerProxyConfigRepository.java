package com.aibook.repository;

import com.aibook.model.entity.CrawlerProxyConfig;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrawlerProxyConfigRepository extends JpaRepository<CrawlerProxyConfig, Long> {
    List<CrawlerProxyConfig> findAllByOrderByPriorityAscIdAsc();
}
