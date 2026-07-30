package com.aibook.repository;

import com.aibook.model.entity.BookVersion;
import com.aibook.model.entity.User;
import com.aibook.model.entity.VersionReadingProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VersionReadingProgressRepository
        extends JpaRepository<VersionReadingProgress, Long> {
    Optional<VersionReadingProgress> findByUserAndVersion(User user, BookVersion version);

    boolean existsByUserAndVersion(User user, BookVersion version);

    void deleteByVersion(BookVersion version);
}
