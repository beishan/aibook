package com.aibook.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Preserve executor identities and queue memberships while removing creation-site ownership. */
@Component
@DependsOn("entityManagerFactory")
@RequiredArgsConstructor
public class CrawlerPublicExecutorsInitializer implements InitializingBean {
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactions;

    @Override
    public void afterPropertiesSet() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.execute("ALTER TABLE crawler_queue_executors ALTER COLUMN queue_id DROP NOT NULL");
            jdbc.update("""
                    UPDATE crawler_queue_executors e SET user_id=COALESCE(q.user_id,s.user_id)
                    FROM crawler_task_queues q LEFT JOIN crawler_sites s ON s.id=q.site_id
                    WHERE e.queue_id=q.id AND e.user_id IS NULL
                    """);
            jdbc.update("""
                    INSERT INTO crawler_executor_shared_queues(executor_id,queue_id)
                    SELECT id,queue_id FROM crawler_queue_executors
                    WHERE queue_id IS NOT NULL AND user_id IS NOT NULL AND assigned IS DISTINCT FROM FALSE
                    ON CONFLICT DO NOTHING
                    """);
            jdbc.update("""
                    UPDATE crawler_queue_executors SET queue_id=NULL,assigned=FALSE
                    WHERE queue_id IS NOT NULL AND user_id IS NOT NULL
                    """);
        });
    }
}
