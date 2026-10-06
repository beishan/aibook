package com.aibook.repository;

import com.aibook.model.entity.CrawlerQueueProxyCooldown;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CrawlerQueueProxyCooldownRepository
        extends JpaRepository<CrawlerQueueProxyCooldown, Long> {
    List<CrawlerQueueProxyCooldown> findByQueueId(Long queueId);

    void deleteByQueueId(Long queueId);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO crawler_queue_proxy_cooldowns
                (queue_id, proxy_key, blocked_until, reason, consecutive_failures)
            VALUES (:queueId, :proxyKey,
                CASE WHEN :threshold <= 1 THEN :until ELSE :now END, :reason, 1)
            ON CONFLICT (queue_id, proxy_key) DO UPDATE SET
                consecutive_failures = COALESCE(crawler_queue_proxy_cooldowns.consecutive_failures, 0) + 1,
                blocked_until = CASE
                    WHEN COALESCE(crawler_queue_proxy_cooldowns.consecutive_failures, 0) + 1 >= :threshold
                    THEN GREATEST(crawler_queue_proxy_cooldowns.blocked_until, :until)
                    ELSE crawler_queue_proxy_cooldowns.blocked_until END,
                reason = :reason
            """, nativeQuery = true)
    void recordFailure(@Param("queueId") Long queueId, @Param("proxyKey") String proxyKey,
            @Param("threshold") int threshold, @Param("now") Instant now,
            @Param("until") Instant until, @Param("reason") String reason);

    @Modifying
    @Transactional
    @Query("update CrawlerQueueProxyCooldown c set c.consecutiveFailures = 0 "
            + "where c.queue.id = :queueId and c.proxyKey = :proxyKey")
    void resetFailures(@Param("queueId") Long queueId, @Param("proxyKey") String proxyKey);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO crawler_queue_proxy_cooldowns
                (queue_id, proxy_key, blocked_until, reason)
            VALUES (:queueId, :proxyKey, :blockedUntil, :reason)
            ON CONFLICT (queue_id, proxy_key)
            DO UPDATE SET blocked_until = GREATEST(
                    crawler_queue_proxy_cooldowns.blocked_until, EXCLUDED.blocked_until),
                reason = EXCLUDED.reason
            """, nativeQuery = true)
    void extend(@Param("queueId") Long queueId,
            @Param("proxyKey") String proxyKey,
            @Param("blockedUntil") Instant blockedUntil,
            @Param("reason") String reason);
}
