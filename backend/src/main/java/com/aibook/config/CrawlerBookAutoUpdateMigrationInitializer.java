package com.aibook.config;

import com.aibook.model.entity.SystemConfig;
import com.aibook.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Stops legacy completed crawler books from being enrolled in automatic updates on upgrade. */
@Component
@RequiredArgsConstructor
@Slf4j
public class CrawlerBookAutoUpdateMigrationInitializer implements ApplicationRunner {

    static final String MIGRATION_KEY = "migration.crawler-book-auto-update.v1";

    private final JdbcTemplate jdbcTemplate;
    private final SystemConfigRepository systemConfigRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (systemConfigRepository.findById(MIGRATION_KEY)
                .map(SystemConfig::getConfigValue)
                .filter("complete"::equals)
                .isPresent()) {
            return;
        }

        int updated = jdbcTemplate.update("""
                UPDATE crawler_books
                SET auto_update_enabled = FALSE
                WHERE crawl_status = 'COMPLETED'
                  AND auto_update_enabled = TRUE
                """);
        systemConfigRepository.save(SystemConfig.builder()
                .configKey(MIGRATION_KEY)
                .configValue("complete")
                .description("历史已完成采集书籍默认停止自动追更")
                .build());
        log.info("历史已完成采集书籍自动追更迁移完成，停止追更 {} 本", updated);
    }
}
