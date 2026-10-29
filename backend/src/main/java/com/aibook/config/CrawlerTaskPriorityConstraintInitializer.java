package com.aibook.config;

import com.aibook.model.entity.CrawlerTask;
import java.util.Arrays;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Updates legacy PostgreSQL constraints before runners can create crawler tasks. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class CrawlerTaskPriorityConstraintInitializer implements ApplicationRunner {

    private static final String CONSTRAINT_NAME = "crawler_tasks_priority_check";

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        String allowedPriorities = Arrays.stream(CrawlerTask.Priority.values())
                .map(priority -> "'" + priority.name() + "'")
                .collect(Collectors.joining(", "));

        jdbcTemplate.execute(
                "ALTER TABLE crawler_tasks DROP CONSTRAINT IF EXISTS "
                        + CONSTRAINT_NAME);
        jdbcTemplate.execute(
                "ALTER TABLE crawler_tasks ADD CONSTRAINT "
                        + CONSTRAINT_NAME
                        + " CHECK (priority IN ("
                        + allowedPriorities
                        + "))");
        log.info("Crawler task priority constraint synchronized with {} priorities",
                CrawlerTask.Priority.values().length);
    }
}
