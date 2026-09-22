package com.aibook.repository;

import com.aibook.model.entity.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.*;

public interface CrawlerChapterRepository extends JpaRepository<CrawlerChapter, Long> {
    List<CrawlerChapter> findByCrawlerBookOrderByChapterIndexAsc(CrawlerBook book);
    List<CrawlerChapter> findByCrawlerBookAndCrawlStatus(
            CrawlerBook book, CrawlerChapter.CrawlStatus status);
    Page<CrawlerChapter> findByCrawlerBook(CrawlerBook book, Pageable pageable);
    Optional<CrawlerChapter> findFirstByCrawlerBookAndCrawlStatusOrderByUpdatedAtDesc(
            CrawlerBook book, CrawlerChapter.CrawlStatus status);
    Optional<CrawlerChapter> findFirstByCrawlerBookAndChapterNameOrderByChapterIndexAsc(
            CrawlerBook book, String chapterName);
    long countByCrawlerBookAndChapterIndexLessThan(CrawlerBook book, Integer chapterIndex);
    Optional<CrawlerChapter> findByCrawlerBookAndExternalChapterId(CrawlerBook book, String externalId);
    long countByCrawlerBookAndCrawlStatus(CrawlerBook book, CrawlerChapter.CrawlStatus status);
    long countByCrawlerBook(CrawlerBook book);
    long countByCrawlerBookSiteUserAndCrawlStatus(User user, CrawlerChapter.CrawlStatus status);
    long countByCrawlerBookSiteUserAndCreatedAtAfter(User user, LocalDateTime start);
    @Query("""
            select cast(c.createdAt as LocalDate), count(c)
            from CrawlerChapter c
            where c.crawlerBook.site.user = :user and c.createdAt >= :start
            group by cast(c.createdAt as LocalDate)
            order by cast(c.createdAt as LocalDate)
            """)
    List<Object[]> countCreatedByDay(@Param("user") User user, @Param("start") LocalDateTime start);
    @Query("""
            select cast(c.crawlTime as LocalDate), count(c)
            from CrawlerChapter c
            where c.crawlerBook.site.user = :user and c.crawlTime >= :start
              and c.crawlStatus = :status
            group by cast(c.crawlTime as LocalDate)
            order by cast(c.crawlTime as LocalDate)
            """)
    List<Object[]> countSuccessfulByDay(@Param("user") User user,
            @Param("start") LocalDateTime start, @Param("status") CrawlerChapter.CrawlStatus status);
    @Query("""
            select c.crawlerBook.site.id, c.crawlerBook.site.siteName, count(c)
            from CrawlerChapter c
            where c.crawlerBook.site.user = :user and c.createdAt >= :start
            group by c.crawlerBook.site.id, c.crawlerBook.site.siteName
            """)
    List<Object[]> countCreatedBySite(@Param("user") User user, @Param("start") LocalDateTime start);
}
