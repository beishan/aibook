package com.aibook.config;

import com.aibook.model.entity.Book;
import java.util.Arrays;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Keeps the PostgreSQL book source type constraint aligned with the Java enum. */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookSourceTypeConstraintInitializer implements ApplicationRunner {

    private static final String CONSTRAINT_NAME = "books_source_type_check";

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        String allowedTypes = Arrays.stream(Book.SourceType.values())
                .map(sourceType -> "'" + sourceType.name() + "'")
                .collect(Collectors.joining(", "));

        jdbcTemplate.execute(
                "ALTER TABLE books DROP CONSTRAINT IF EXISTS " + CONSTRAINT_NAME);
        jdbcTemplate.execute(
                "ALTER TABLE books ADD CONSTRAINT "
                        + CONSTRAINT_NAME
                        + " CHECK (source_type IN ("
                        + allowedTypes
                        + "))");
        log.info("Book source type constraint synchronized with {} source types",
                Book.SourceType.values().length);
    }
}
