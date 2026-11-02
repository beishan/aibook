package com.aibook.repository;

import com.aibook.model.entity.CrawlerBlacklistScreenJob;
import com.aibook.model.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.transaction.annotation.Transactional;

public interface CrawlerBlacklistScreenJobRepository extends JpaRepository<CrawlerBlacklistScreenJob, String> {
    Optional<CrawlerBlacklistScreenJob> findByIdAndUser(String id, User user);
    Optional<CrawlerBlacklistScreenJob> findFirstByUserOrderByCreatedAtDesc(User user);

    @Transactional
    @Modifying
    @Query("update CrawlerBlacklistScreenJob j set j.status = 'FAILED', "
            + "j.errorMessage = '服务重启，筛选已中断，请重新开始', j.finishedAt = CURRENT_TIMESTAMP "
            + "where j.status = 'RUNNING'")
    int interruptUnfinished();
}
