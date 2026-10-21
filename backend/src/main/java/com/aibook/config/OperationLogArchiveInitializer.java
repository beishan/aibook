package com.aibook.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Retain historical actor identity while allowing an account to be removed. */
@Component
@RequiredArgsConstructor
public class OperationLogArchiveInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        jdbc.execute("ALTER TABLE operation_logs ALTER COLUMN user_id DROP NOT NULL");
        jdbc.update("""
                UPDATE operation_logs l
                SET owner_user_id = COALESCE(l.owner_user_id, l.user_id),
                    owner_username = COALESCE(l.owner_username, u.username)
                FROM users u
                WHERE l.user_id = u.id
                  AND (l.owner_user_id IS NULL OR l.owner_username IS NULL)
                """);
    }
}
