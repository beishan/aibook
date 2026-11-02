package com.aibook.repository;

import com.aibook.model.entity.CrawlerBlacklistScreenResult;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CrawlerBlacklistScreenResultRepository extends JpaRepository<CrawlerBlacklistScreenResult, Long> {
    Page<CrawlerBlacklistScreenResult> findByJobIdOrderByIdAsc(String jobId, Pageable pageable);

    @Query("select r.bookId from CrawlerBlacklistScreenResult r "
            + "where r.job.id = :jobId and r.bookId in :ids")
    List<Long> matchedIds(@Param("jobId") String jobId, @Param("ids") Collection<Long> ids);

    @Query("select r.bookId from CrawlerBlacklistScreenResult r "
            + "where r.job.id = :jobId and r.bookId in :ids and coalesce(r.ignored, false) = false")
    List<Long> eligibleMatchedIds(@Param("jobId") String jobId, @Param("ids") Collection<Long> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CrawlerBlacklistScreenResult r set r.ignored = :ignored "
            + "where r.job.id = :jobId and r.bookId = :bookId")
    int updateIgnored(@Param("jobId") String jobId, @Param("bookId") Long bookId,
            @Param("ignored") boolean ignored);
}
