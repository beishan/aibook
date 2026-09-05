package com.aibook.repository;

import com.aibook.model.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface CrawlerBookExportRepository extends JpaRepository<CrawlerBookExport, Long> {
    List<CrawlerBookExport> findByCrawlerBookOrderByFormatAsc(CrawlerBook book);
    Optional<CrawlerBookExport> findByCrawlerBookAndFormat(CrawlerBook book, String format);
}
