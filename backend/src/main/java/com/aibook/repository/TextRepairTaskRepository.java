package com.aibook.repository;

import com.aibook.model.entity.TextRepairTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TextRepairTaskRepository extends JpaRepository<TextRepairTask, Long> {

    List<TextRepairTask> findByBookIdOrderByCreatedAtDesc(Long bookId);

    Page<TextRepairTask> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<TextRepairTask> findByUserIdAndStatusInOrderByCreatedAtDesc(
            Long userId, List<String> statuses, Pageable pageable);

    List<TextRepairTask> findByUserIdAndStatus(Long userId, String status);
}
