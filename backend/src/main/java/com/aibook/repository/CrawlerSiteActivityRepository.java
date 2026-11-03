package com.aibook.repository;

import com.aibook.model.entity.CrawlerSite;
import com.aibook.model.entity.CrawlerSiteActivity;
import java.time.LocalDateTime;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CrawlerSiteActivityRepository extends JpaRepository<CrawlerSiteActivity, Long> {

    Page<CrawlerSiteActivity> findBySiteOrderByCreatedAtDescIdDesc(
            CrawlerSite site, Pageable pageable);

    Page<CrawlerSiteActivity> findBySiteAndEventTypeOrderByCreatedAtDescIdDesc(
            CrawlerSite site, CrawlerSiteActivity.EventType eventType, Pageable pageable);

    boolean existsByTaskIdAndEventType(String taskId, CrawlerSiteActivity.EventType eventType);

    void deleteBySite(CrawlerSite site);

    @Modifying
    @Query("update CrawlerSiteActivity a set a.retained = :retained where a.site = :site and a.id in :ids")
    int setRetained(@Param("site") CrawlerSite site, @Param("ids") Collection<Long> ids,
            @Param("retained") boolean retained);

    @Modifying
    @Query("delete from CrawlerSiteActivity a where a.site = :site and a.id in :ids")
    int deleteSelected(@Param("site") CrawlerSite site, @Param("ids") Collection<Long> ids);

    @Modifying
    @Query("delete from CrawlerSiteActivity a where a.site = :site and coalesce(a.retained, false) = false")
    int deleteUnretained(@Param("site") CrawlerSite site);

    @Modifying
    @Query("delete from CrawlerSiteActivity a where a.site = :site and coalesce(a.retained, false) = false "
            + "and a.createdAt < :cutoff")
    int deleteExpired(@Param("site") CrawlerSite site, @Param("cutoff") LocalDateTime cutoff);
}
