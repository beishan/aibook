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
