package com.aibook.repository;

import com.aibook.model.entity.CrawlerMihomoPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CrawlerMihomoPolicyRepository extends JpaRepository<CrawlerMihomoPolicy, Long> {
    /** Never merge an old chapter's configuration back over a newly saved configuration. */
    @Modifying
    @Transactional
    @Query("""
            update CrawlerMihomoPolicy p set
                p.currentNode = :#{#state.currentNode},
                p.failures = :#{#state.failures},
                p.activeMillis = :#{#state.activeMillis},
                p.chapters = :#{#state.chapters},
                p.tasks = :#{#state.tasks},
                p.lastActivityAt = :#{#state.lastActivityAt},
                p.lastSwitchAt = :#{#state.lastSwitchAt},
                p.retryAt = :#{#state.retryAt},
                p.lastError = :#{#state.lastError},
                p.cooldownsJson = :#{#state.cooldownsJson},
                p.nodeFailuresJson = :#{#state.nodeFailuresJson},
                p.cooldownReasonsJson = :#{#state.cooldownReasonsJson},
                p.eventsJson = :#{#state.eventsJson}
            where p.executorId = :#{#state.executorId}
                and coalesce(p.configurationRevision, 0) = :revision
            """)
    int updateRuntime(@Param("state") CrawlerMihomoPolicy state, @Param("revision") long revision);

    boolean existsByNodeGroupId(Long nodeGroupId);
    boolean existsBySystemProxyId(Long systemProxyId);
    boolean existsByControllerUrlAndGroupNameAndExecutorIdNot(
            String controllerUrl, String groupName, Long executorId);
}
