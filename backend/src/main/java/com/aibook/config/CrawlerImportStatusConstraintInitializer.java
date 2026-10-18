package com.aibook.config;

import com.aibook.model.entity.CrawlerBook;
import java.util.Arrays;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 升级已有数据库的枚举约束，允许保存入库失败状态。 */
@Component
@RequiredArgsConstructor
public class CrawlerImportStatusConstraintInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        String statuses = Arrays.stream(CrawlerBook.ImportStatus.values())
                .map(status -> "'" + status.name() + "'")
                .collect(Collectors.joining(", "));
        jdbcTemplate.execute("ALTER TABLE crawler_books DROP CONSTRAINT IF EXISTS "
                + "crawler_books_import_status_check");
        jdbcTemplate.execute("ALTER TABLE crawler_books ADD CONSTRAINT "
                + "crawler_books_import_status_check CHECK (import_status IN (" + statuses + "))");
    }
}
