package com.aibook.repository;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.ReadingProgress;
import com.aibook.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 阅读进度 Repository
 */
@Repository
public interface ReadingProgressRepository extends JpaRepository<ReadingProgress, Long> {

    /**
     * 根据用户和书籍查询阅读进度
     */
    Optional<ReadingProgress> findByUserAndBook(User user, Book book);

    /**
     * 根据用户查询最近阅读的书籍
     */
    Optional<ReadingProgress> findTopByUserOrderByLastReadAtDesc(User user);
}
