package com.aibook.repository;

import com.aibook.model.entity.CrawlerSite;
import com.aibook.model.entity.CrawlerSiteActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrawlerSiteActivityRepository extends JpaRepository<CrawlerSiteActivity, Long> {

    Page<CrawlerSiteActivity> findBySiteOrderByCreatedAtDescIdDesc(
            CrawlerSite site, Pageable pageable);

    Page<CrawlerSiteActivity> findBySiteAndEventTypeOrderByCreatedAtDescIdDesc(
            CrawlerSite site, CrawlerSiteActivity.EventType eventType, Pageable pageable);

    boolean existsByTaskIdAndEventType(String taskId, CrawlerSiteActivity.EventType eventType);

    void deleteBySite(CrawlerSite site);
}
