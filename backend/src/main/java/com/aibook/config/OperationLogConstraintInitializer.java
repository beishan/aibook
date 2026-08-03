package com.aibook.config;

import com.aibook.model.entity.OperationLog;
import java.util.Arrays;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Keeps the PostgreSQL operation log action constraint aligned with the Java enum. */
@Component
@RequiredArgsConstructor
@Slf4j
public class OperationLogConstraintInitializer implements ApplicationRunner {

    private static final String CONSTRAINT_NAME = "operation_logs_action_check";

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        String allowedActions = Arrays.stream(OperationLog.Action.values())
                .map(action -> "'" + action.name() + "'")
                .collect(Collectors.joining(", "));

        jdbcTemplate.execute(
                "ALTER TABLE operation_logs DROP CONSTRAINT IF EXISTS " + CONSTRAINT_NAME);
        jdbcTemplate.execute(
                "ALTER TABLE operation_logs ADD CONSTRAINT "
                        + CONSTRAINT_NAME
                        + " CHECK (action IN ("
                        + allowedActions
                        + "))");
        log.info("Operation log action constraint synchronized with {} actions",
                OperationLog.Action.values().length);
    }
}
