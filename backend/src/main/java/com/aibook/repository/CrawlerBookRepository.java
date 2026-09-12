package com.aibook.repository;

import com.aibook.model.entity.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface CrawlerBookRepository extends JpaRepository<CrawlerBook, Long> {
    Optional<CrawlerBook> findByIdAndSiteUser(Long id, User user);
    Optional<CrawlerBook> findBySiteAndExternalBookId(CrawlerSite site, String externalBookId);
    Optional<CrawlerBook> findFirstBySiteOrderByLastCrawlTimeDesc(CrawlerSite site);
    Page<CrawlerBook> findBySiteUser(User user, Pageable pageable);
    @Query("""
            select b from CrawlerBook b
            where b.site.user = :user
              and b.discoveryStatus = :discoveryStatus
              and b.crawlStatus <> :excludedCrawlStatus
              and (:siteId is null or b.site.id = :siteId)
              and (:crawlStatus is null or b.crawlStatus = :crawlStatus)
              and (:importStatus is null or b.importStatus = :importStatus)
              and (:keyword = ''
                   or lower(b.bookName) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(b.author, '')) like lower(concat('%', :keyword, '%'))
                   or lower(b.site.siteName) like lower(concat('%', :keyword, '%'))
                   or lower(b.externalBookId) like lower(concat('%', :keyword, '%')))
            """)
    Page<CrawlerBook> searchManagedBooks(
            @Param("user") User user,
            @Param("discoveryStatus") CrawlerBook.DiscoveryStatus discoveryStatus,
            @Param("excludedCrawlStatus") CrawlerBook.CrawlStatus excludedCrawlStatus,
            @Param("keyword") String keyword,
            @Param("siteId") Long siteId,
            @Param("crawlStatus") CrawlerBook.CrawlStatus crawlStatus,
            @Param("importStatus") CrawlerBook.ImportStatus importStatus,
            Pageable pageable);
    @Query("""
            select b from CrawlerBook b
            where b.site.user = :user
              and b.discoveryStatus = :discoveryStatus
              and b.crawlStatus = :crawlStatus
              and (:siteId is null or b.site.id = :siteId)
              and (:keyword = ''
                   or lower(b.bookName) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(b.author, '')) like lower(concat('%', :keyword, '%'))
                   or lower(b.site.siteName) like lower(concat('%', :keyword, '%'))
                   or lower(b.externalBookId) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(b.latestChapter, '')) like lower(concat('%', :keyword, '%')))
            """)
    Page<CrawlerBook> searchDiscoveredBooks(
            @Param("user") User user,
            @Param("discoveryStatus") CrawlerBook.DiscoveryStatus discoveryStatus,
            @Param("crawlStatus") CrawlerBook.CrawlStatus crawlStatus,
            @Param("keyword") String keyword,
            @Param("siteId") Long siteId,
            Pageable pageable);
    long countBySiteUser(User user);
    long countBySite(CrawlerSite site);
    boolean existsBySite(CrawlerSite site);
    List<CrawlerBook> findBySiteAndCrawlStatusIn(CrawlerSite site, Collection<CrawlerBook.CrawlStatus> statuses);
    long countBySiteUserAndCrawlStatus(User user, CrawlerBook.CrawlStatus status);
    long countBySiteUserAndImportStatus(User user, CrawlerBook.ImportStatus status);
    @Query("select count(b) from CrawlerBook b where b.site.user = :user and b.createdAt >= :start")
    long countCreatedSince(@Param("user") User user, @Param("start") java.time.LocalDateTime start);
}
