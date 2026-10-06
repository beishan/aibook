package com.aibook.config;

import com.aibook.model.entity.CrawlerQueueExecutor;
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

/** Updates legacy PostgreSQL constraints before runners can create Mihomo executors. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class CrawlerQueueExecutorProxyModeConstraintInitializer implements ApplicationRunner {

    private static final String CONSTRAINT_NAME = "crawler_queue_executors_proxy_mode_check";

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        String allowedModes = Arrays.stream(CrawlerQueueExecutor.ProxyMode.values())
                .map(mode -> "'" + mode.name() + "'")
                .collect(Collectors.joining(", "));

        jdbcTemplate.execute(
                "ALTER TABLE crawler_queue_executors DROP CONSTRAINT IF EXISTS "
                        + CONSTRAINT_NAME);
        jdbcTemplate.execute(
                "ALTER TABLE crawler_queue_executors ADD CONSTRAINT "
                        + CONSTRAINT_NAME
                        + " CHECK (proxy_mode IN ("
                        + allowedModes
                        + "))");
        log.info("Crawler executor proxy mode constraint synchronized with {} modes",
                CrawlerQueueExecutor.ProxyMode.values().length);
    }
}
