package com.aibook.repository;

import com.aibook.model.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.*;

public interface CrawlerChapterRepository extends JpaRepository<CrawlerChapter, Long> {
    List<CrawlerChapter> findByCrawlerBookOrderByChapterIndexAsc(CrawlerBook book);
    Optional<CrawlerChapter> findByCrawlerBookAndExternalChapterId(CrawlerBook book, String externalId);
    long countByCrawlerBookAndCrawlStatus(CrawlerBook book, CrawlerChapter.CrawlStatus status);
    long countByCrawlerBook(CrawlerBook book);
    long countByCrawlerBookSiteUserAndCrawlStatus(User user, CrawlerChapter.CrawlStatus status);
    long countByCrawlerBookSiteUserAndCreatedAtAfter(User user, LocalDateTime start);
}
