package com.aibook.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Disables legacy crawler automation flags after switching task creation to manual-only mode. */
@Component
@RequiredArgsConstructor
@Slf4j
public class CrawlerManualModeInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        int sites = jdbcTemplate.update("""
                UPDATE crawler_sites
                SET auto_scan = FALSE,
                    auto_crawl = FALSE,
                    auto_update = FALSE,
                    auto_import_library = FALSE
                WHERE auto_scan = TRUE
                   OR auto_crawl = TRUE
                   OR auto_update = TRUE
                   OR auto_import_library = TRUE
                """);
        int books = jdbcTemplate.update("""
                UPDATE crawler_books
                SET auto_update_enabled = FALSE
                WHERE auto_update_enabled = TRUE
                """);
        if (sites > 0 || books > 0) {
            log.info("爬虫已切换为仅手动触发模式，关闭自动配置：网站 {} 个，书籍 {} 本", sites, books);
        }
    }
}
