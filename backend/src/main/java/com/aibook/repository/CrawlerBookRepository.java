package com.aibook.repository;

import com.aibook.model.entity.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface CrawlerBookRepository extends JpaRepository<CrawlerBook, Long> {
    Optional<CrawlerBook> findByIdAndSiteUser(Long id, User user);
    Optional<CrawlerBook> findBySiteAndExternalBookId(CrawlerSite site, String externalBookId);
    Page<CrawlerBook> findBySiteUser(User user, Pageable pageable);
    long countBySiteUser(User user);
    long countBySite(CrawlerSite site);
    boolean existsBySite(CrawlerSite site);
    long countBySiteUserAndCrawlStatus(User user, CrawlerBook.CrawlStatus status);
    long countBySiteUserAndImportStatus(User user, CrawlerBook.ImportStatus status);
    @Query("select count(b) from CrawlerBook b where b.site.user = :user and b.createdAt >= :start")
    long countCreatedSince(@Param("user") User user, @Param("start") java.time.LocalDateTime start);
}
