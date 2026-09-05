package com.aibook.repository;

import com.aibook.model.entity.CrawlerSite;
import com.aibook.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface CrawlerSiteRepository extends JpaRepository<CrawlerSite, Long> {
    List<CrawlerSite> findByUserOrderByCreatedAtDesc(User user);
    List<CrawlerSite> findByEnabledTrue();
    Optional<CrawlerSite> findByIdAndUser(Long id, User user);
    Optional<CrawlerSite> findByUserAndSiteCode(User user, String siteCode);
}
