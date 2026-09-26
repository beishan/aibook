package com.aibook.repository;

import com.aibook.model.entity.CrawlerSite;
import com.aibook.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;

public interface CrawlerSiteRepository extends JpaRepository<CrawlerSite, Long> {
    List<CrawlerSite> findByUserOrderByCreatedAtDesc(User user);
    List<CrawlerSite> findByEnabledTrue();
    Optional<CrawlerSite> findByIdAndUser(Long id, User user);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CrawlerSite> findLockedByIdAndUser(Long id, User user);
    Optional<CrawlerSite> findByUserAndSiteCode(User user, String siteCode);

    @Modifying
    @Transactional
    @Query("update CrawlerSite s set s.crawlerBlockedUntil = :blockedUntil, "
            + "s.crawlerBlockReason = :reason where s.id = :siteId")
    int updateCrawlerProtection(@Param("siteId") Long siteId,
            @Param("blockedUntil") Instant blockedUntil, @Param("reason") String reason);

    @Modifying
    @Transactional
    @Query("update CrawlerSite s set s.crawlerBlockUrl = :pageUrl where s.id = :siteId")
    int updateCrawlerProtectionUrl(@Param("siteId") Long siteId,
            @Param("pageUrl") String pageUrl);
}
