package com.aibook.repository;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.Bookmark;
import com.aibook.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 书签 Repository
 */
@Repository
public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    /**
     * 查询用户在某本书中的所有书签
     */
    List<Bookmark> findByUserAndBookOrderByCreatedAtDesc(User user, Book book);

    /**
     * 删除用户在某本书中的指定书签
     */
    void deleteByUserAndBookAndId(User user, Book book, Long id);

    /**
     * 查询用户在某本书中的书签数量
     */
    long countByUserAndBook(User user, Book book);

    /**
     * 根据书籍删除所有书签
     */
    void deleteByBook(Book book);
}
