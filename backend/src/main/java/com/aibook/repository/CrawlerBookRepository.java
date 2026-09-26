package com.aibook.repository;

import com.aibook.model.entity.*;
import com.aibook.repository.projections.BookTitleMatchProjection;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface CrawlerBookRepository extends JpaRepository<CrawlerBook, Long> {
    Optional<CrawlerBook> findByIdAndSiteUser(Long id, User user);
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CrawlerBook b set b.favorite = :favorite where b.id = :id and b.site.user = :user")
    int updateFavorite(@Param("id") Long id, @Param("user") User user,
            @Param("favorite") boolean favorite);
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
              and (:favoriteOnly = false or b.favorite = true)
              and (:keyword = ''
                   or lower(b.bookName) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(b.author, '')) like lower(concat('%', :keyword, '%'))
                   or lower(b.site.siteName) like lower(concat('%', :keyword, '%'))
                   or lower(b.externalBookId) like lower(concat('%', :keyword, '%')))
            order by case when b.crawlStatus in :runningStatuses then 0 else 1 end
            """)
    Page<CrawlerBook> searchManagedBooks(
            @Param("user") User user,
            @Param("discoveryStatus") CrawlerBook.DiscoveryStatus discoveryStatus,
            @Param("excludedCrawlStatus") CrawlerBook.CrawlStatus excludedCrawlStatus,
            @Param("keyword") String keyword,
            @Param("siteId") Long siteId,
            @Param("crawlStatus") CrawlerBook.CrawlStatus crawlStatus,
            @Param("importStatus") CrawlerBook.ImportStatus importStatus,
            @Param("favoriteOnly") boolean favoriteOnly,
            @Param("runningStatuses") Collection<CrawlerBook.CrawlStatus> runningStatuses,
            Pageable pageable);
    default Page<CrawlerBook> searchManagedBooks(
            User user, CrawlerBook.DiscoveryStatus discoveryStatus,
            CrawlerBook.CrawlStatus excludedCrawlStatus, String keyword, Long siteId,
            CrawlerBook.CrawlStatus crawlStatus, CrawlerBook.ImportStatus importStatus,
            Collection<CrawlerBook.CrawlStatus> runningStatuses, Pageable pageable) {
        return searchManagedBooks(user, discoveryStatus, excludedCrawlStatus, keyword, siteId,
                crawlStatus, importStatus, false, runningStatuses, pageable);
    }
    @Query("""
            select b from CrawlerBook b
            where b.site.user = :user
              and b.discoveryStatus = :discoveryStatus
              and b.crawlStatus = :crawlStatus
              and (:siteId is null or b.site.id = :siteId)
              and (:favoriteOnly = false or b.favorite = true)
              and (:keyword = ''
                   or lower(b.bookName) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(b.author, '')) like lower(concat('%', :keyword, '%'))
                   or lower(b.site.siteName) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(b.discoveryPageName, '')) like lower(concat('%', :keyword, '%'))
                   or lower(b.externalBookId) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(b.latestChapter, '')) like lower(concat('%', :keyword, '%')))
            """)
    Page<CrawlerBook> searchDiscoveredBooks(
            @Param("user") User user,
            @Param("discoveryStatus") CrawlerBook.DiscoveryStatus discoveryStatus,
            @Param("crawlStatus") CrawlerBook.CrawlStatus crawlStatus,
            @Param("keyword") String keyword,
            @Param("siteId") Long siteId,
            @Param("favoriteOnly") boolean favoriteOnly,
            Pageable pageable);
    default Page<CrawlerBook> searchDiscoveredBooks(
            User user, CrawlerBook.DiscoveryStatus discoveryStatus,
            CrawlerBook.CrawlStatus crawlStatus, String keyword, Long siteId, Pageable pageable) {
        return searchDiscoveredBooks(user, discoveryStatus, crawlStatus, keyword, siteId, false, pageable);
    }
    @Query("""
            select lower(trim(b.bookName)) as normalizedTitle, b.id as recordId
            from CrawlerBook b
            where b.site.user = :user
              and lower(trim(b.bookName)) in :normalizedTitles
            """)
    List<BookTitleMatchProjection> findTitleMatchesByUser(
            @Param("user") User user,
            @Param("normalizedTitles") Collection<String> normalizedTitles);
    long countBySiteUser(User user);
    long countBySite(CrawlerSite site);
    boolean existsBySite(CrawlerSite site);
    List<CrawlerBook> findBySiteAndCrawlStatusIn(CrawlerSite site, Collection<CrawlerBook.CrawlStatus> statuses);
    long countBySiteUserAndCrawlStatus(User user, CrawlerBook.CrawlStatus status);
    long countBySiteUserAndImportStatus(User user, CrawlerBook.ImportStatus status);
    @Query("select count(b) from CrawlerBook b where b.site.user = :user and b.createdAt >= :start")
    long countCreatedSince(@Param("user") User user, @Param("start") java.time.LocalDateTime start);
    @Query("""
            select cast(b.createdAt as LocalDate), count(b)
            from CrawlerBook b
            where b.site.user = :user and b.createdAt >= :start
            group by cast(b.createdAt as LocalDate)
            order by cast(b.createdAt as LocalDate)
            """)
    List<Object[]> countCreatedByDay(@Param("user") User user,
            @Param("start") java.time.LocalDateTime start);
    @Query("""
            select cast(b.lastCrawlTime as LocalDate), count(b)
            from CrawlerBook b
            where b.site.user = :user and b.crawlStatus = :completedStatus
              and b.lastCrawlTime >= :start
            group by cast(b.lastCrawlTime as LocalDate)
            order by cast(b.lastCrawlTime as LocalDate)
            """)
    List<Object[]> countSuccessfullyCrawledByDay(@Param("user") User user,
            @Param("start") java.time.LocalDateTime start,
            @Param("completedStatus") CrawlerBook.CrawlStatus completedStatus);
    @Query("""
            select b.site.id, b.site.siteName, count(b)
            from CrawlerBook b
            where b.site.user = :user and b.createdAt >= :start
            group by b.site.id, b.site.siteName
            """)
    List<Object[]> countCreatedBySite(@Param("user") User user,
            @Param("start") java.time.LocalDateTime start);
}
