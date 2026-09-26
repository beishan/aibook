package com.aibook.repository;

import com.aibook.model.entity.CrawlerTaskQueue;
import com.aibook.model.entity.CrawlerSite;
import com.aibook.model.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrawlerTaskQueueRepository extends JpaRepository<CrawlerTaskQueue, Long> {
    Optional<CrawlerTaskQueue> findBySite(CrawlerSite site);

    List<CrawlerTaskQueue> findBySite_UserOrderBySite_SiteNameAsc(User user);

    Optional<CrawlerTaskQueue> findBySiteIdAndSiteUserId(Long siteId, Long userId);

    void deleteBySite(CrawlerSite site);
}
