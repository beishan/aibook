package com.aibook.repository;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookHighlight;
import com.aibook.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 书籍高亮 Repository
 */
@Repository
public interface BookHighlightRepository extends JpaRepository<BookHighlight, Long> {

    /**
     * 查询用户在某本书中的所有高亮
     */
    List<BookHighlight> findByUserAndBookOrderByCreatedAtDesc(User user, Book book);

    /**
     * 根据 CFI 范围查找高亮（用于去重）
     */
    Optional<BookHighlight> findByUserAndBookAndCfiRange(User user, Book book, String cfiRange);

    /**
     * 删除用户在某本书中的指定高亮
     */
    void deleteByUserAndBookAndId(User user, Book book, Long id);

    /**
     * 查询用户在某本书中的高亮数量
     */
    long countByUserAndBook(User user, Book book);
}
