package com.aibook.repository;

import com.aibook.model.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface CrawlerSiteRuleVersionRepository extends JpaRepository<CrawlerSiteRuleVersion, Long> {
    List<CrawlerSiteRuleVersion> findBySiteOrderByVersionDesc(CrawlerSite site);
    Optional<CrawlerSiteRuleVersion> findByIdAndSite(Long id, CrawlerSite site);
    Optional<CrawlerSiteRuleVersion> findBySiteAndVersion(CrawlerSite site, Integer version);
    Optional<CrawlerSiteRuleVersion> findFirstBySiteAndEnabledTrue(CrawlerSite site);
    long countBySite(CrawlerSite site);
    boolean existsBySite(CrawlerSite site);
    void deleteBySite(CrawlerSite site);
}
