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
}
