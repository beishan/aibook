package com.aibook.repository;

import com.aibook.model.entity.CrawlerDiscoveryPage;
import com.aibook.model.entity.CrawlerSite;
import com.aibook.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CrawlerDiscoveryPageRepository extends JpaRepository<CrawlerDiscoveryPage, Long> {
    List<CrawlerDiscoveryPage> findBySiteUserOrderByCreatedAtAsc(User user);
    List<CrawlerDiscoveryPage> findBySiteOrderByCreatedAtAsc(CrawlerSite site);
    List<CrawlerDiscoveryPage> findByAutoScanEnabledTrueAndSiteEnabledTrue();
    Optional<CrawlerDiscoveryPage> findByIdAndSiteUser(Long id, User user);
    boolean existsBySiteAndPageNameIgnoreCaseAndIdNot(CrawlerSite site, String pageName, Long id);
    void deleteBySite(CrawlerSite site);
}
