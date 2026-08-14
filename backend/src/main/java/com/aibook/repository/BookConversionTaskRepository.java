package com.aibook.repository;

import com.aibook.model.entity.BookConversionTask;
import com.aibook.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookConversionTaskRepository extends JpaRepository<BookConversionTask, Long> {
    Optional<BookConversionTask> findByIdAndUser(Long id, User user);
    List<BookConversionTask> findByUserOrderByCreatedAtDesc(User user);
    List<BookConversionTask> findByExpiresAtBefore(LocalDateTime expiresAt);
}
