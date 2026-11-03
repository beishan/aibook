package com.aibook.repository;

import com.aibook.model.entity.CrawlerTaskLogRetention;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CrawlerTaskLogRetentionRepository extends JpaRepository<CrawlerTaskLogRetention, Long> {
    List<CrawlerTaskLogRetention> findByUserId(Long userId);
    @Query("select distinct p.user.id from CrawlerTaskLogRetention p "
            + "where p.automatic = true and p.retentionDays > 0 order by p.user.id")
    List<Long> findAutomaticUserIds();
}
