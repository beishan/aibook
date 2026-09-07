package com.aibook.repository;

import com.aibook.model.entity.CrawlerSite;
import com.aibook.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface CrawlerSiteRepository extends JpaRepository<CrawlerSite, Long> {
    List<CrawlerSite> findByUserOrderByCreatedAtDesc(User user);
    List<CrawlerSite> findByEnabledTrue();
    Optional<CrawlerSite> findByIdAndUser(Long id, User user);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CrawlerSite> findLockedByIdAndUser(Long id, User user);
    Optional<CrawlerSite> findByUserAndSiteCode(User user, String siteCode);
}
