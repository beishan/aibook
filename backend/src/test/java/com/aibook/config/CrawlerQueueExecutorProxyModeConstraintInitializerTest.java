package com.aibook.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

class CrawlerQueueExecutorProxyModeConstraintInitializerTest {

    private static final String DROP_CONSTRAINT =
            "ALTER TABLE crawler_queue_executors DROP CONSTRAINT IF EXISTS "
                    + "crawler_queue_executors_proxy_mode_check";
    private static final String ADD_CONSTRAINT =
            "ALTER TABLE crawler_queue_executors ADD CONSTRAINT "
                    + "crawler_queue_executors_proxy_mode_check "
                    + "CHECK (proxy_mode IN ('DEFAULT', 'SELECTED', 'MIHOMO'))";

    @Test
    void replacesLegacyConstraintAndKeepsOrdinaryModesOnRepeatedStartup() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CrawlerQueueExecutorProxyModeConstraintInitializer initializer =
                new CrawlerQueueExecutorProxyModeConstraintInitializer(jdbcTemplate);

        initializer.run(new DefaultApplicationArguments());
        initializer.run(new DefaultApplicationArguments());

        InOrder sql = inOrder(jdbcTemplate);
        sql.verify(jdbcTemplate).execute(DROP_CONSTRAINT);
        sql.verify(jdbcTemplate).execute(ADD_CONSTRAINT);
        sql.verify(jdbcTemplate).execute(DROP_CONSTRAINT);
        sql.verify(jdbcTemplate).execute(ADD_CONSTRAINT);
        verifyNoMoreInteractions(jdbcTemplate);
    }

    @Test
    void propagatesConstraintFailureForTransactionalRollback() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("Invalid existing proxy mode");
        doThrow(failure).when(jdbcTemplate).execute(ADD_CONSTRAINT);
        CrawlerQueueExecutorProxyModeConstraintInitializer initializer =
                new CrawlerQueueExecutorProxyModeConstraintInitializer(jdbcTemplate);

        assertThatThrownBy(() -> initializer.run(new DefaultApplicationArguments()))
                .isSameAs(failure);
        assertThat(CrawlerQueueExecutorProxyModeConstraintInitializer.class
                .getMethod("run", org.springframework.boot.ApplicationArguments.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }
}
