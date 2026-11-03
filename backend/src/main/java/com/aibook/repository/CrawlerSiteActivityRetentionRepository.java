package com.aibook.repository;

import com.aibook.model.entity.CrawlerSiteActivityRetention;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CrawlerSiteActivityRetentionRepository extends JpaRepository<CrawlerSiteActivityRetention, Long> {
    Optional<CrawlerSiteActivityRetention> findBySiteId(Long siteId);

    @Query("select p.site.id from CrawlerSiteActivityRetention p "
            + "where p.automatic = true and p.retentionDays > 0 order by p.site.id")
    List<Long> findAutomaticSiteIds();
}
