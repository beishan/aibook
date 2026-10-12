package com.aibook.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

class CrawlerQueueProxyCooldownQueryTest {
    @Test
    void bothCaseBranchesAndCooldownExtensionHaveExplicitTimezoneType() throws Exception {
        Query query = CrawlerQueueProxyCooldownRepository.class.getMethod("recordFailure",
                Long.class, String.class, int.class, Instant.class, Instant.class, String.class)
                .getAnnotation(Query.class);
        assertThat(query.nativeQuery()).isTrue();
        assertThat(query.value()).contains(
                "THEN CAST(:until AS timestamp with time zone)",
                "ELSE CAST(:now AS timestamp with time zone)",
                "CAST(:until AS timestamp with time zone))");
        assertThat(query.value()).doesNotContain("THEN :until", "ELSE :now");
        assertThat(query.value()).contains(
                "ON CONFLICT (queue_id, proxy_key) DO UPDATE",
                "consecutive_failures, 0) + 1 >= :threshold",
                "GREATEST(crawler_queue_proxy_cooldowns.blocked_until",
                "ELSE crawler_queue_proxy_cooldowns.blocked_until END");
    }

    @Test
    void directCooldownUpsertAlsoTypesInstantAndPreservesLaterExpiry() throws Exception {
        String query = CrawlerQueueProxyCooldownRepository.class.getMethod("extend",
                Long.class, String.class, Instant.class, String.class).getAnnotation(Query.class).value();
        assertThat(query).contains("CAST(:blockedUntil AS timestamp with time zone)",
                "crawler_queue_proxy_cooldowns.blocked_until, EXCLUDED.blocked_until");
    }
}
