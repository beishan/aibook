package com.aibook.repository;

import com.aibook.model.entity.CrawlerMihomoPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrawlerMihomoPolicyRepository extends JpaRepository<CrawlerMihomoPolicy, Long> {
    boolean existsByControllerUrlAndGroupNameAndExecutorIdNot(
            String controllerUrl, String groupName, Long executorId);
}
