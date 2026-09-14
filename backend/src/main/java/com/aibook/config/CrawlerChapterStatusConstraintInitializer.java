package com.aibook.config;

import com.aibook.model.entity.CrawlerChapter;
import java.util.Arrays;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Keeps the PostgreSQL crawler chapter status constraint aligned with the Java enum. */
@Component
@RequiredArgsConstructor
@Slf4j
public class CrawlerChapterStatusConstraintInitializer implements ApplicationRunner {

    private static final String CONSTRAINT_NAME = "crawler_chapters_crawl_status_check";

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        String allowedStatuses = Arrays.stream(CrawlerChapter.CrawlStatus.values())
                .map(status -> "'" + status.name() + "'")
                .collect(Collectors.joining(", "));

        jdbcTemplate.execute(
                "ALTER TABLE crawler_chapters DROP CONSTRAINT IF EXISTS " + CONSTRAINT_NAME);
        jdbcTemplate.execute(
                "ALTER TABLE crawler_chapters ADD CONSTRAINT "
                        + CONSTRAINT_NAME
                        + " CHECK (crawl_status IN ("
                        + allowedStatuses
                        + "))");
        log.info("Crawler chapter status constraint synchronized with {} statuses",
                CrawlerChapter.CrawlStatus.values().length);
    }
}
