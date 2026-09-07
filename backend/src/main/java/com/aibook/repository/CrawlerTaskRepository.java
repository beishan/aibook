package com.aibook.repository;

import com.aibook.model.entity.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface CrawlerTaskRepository extends JpaRepository<CrawlerTask, String> {
    Optional<CrawlerTask> findByIdAndUser(String id, User user);
    List<CrawlerTask> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
    List<CrawlerTask> findByStatusIn(Collection<CrawlerTask.TaskStatus> statuses);
    long countByUserAndStatus(User user, CrawlerTask.TaskStatus status);
    boolean existsBySiteAndTypeAndStatusIn(CrawlerSite site, CrawlerTask.TaskType type, Collection<CrawlerTask.TaskStatus> statuses);
    boolean existsByCrawlerBookAndStatusIn(CrawlerBook book, Collection<CrawlerTask.TaskStatus> statuses);
}
