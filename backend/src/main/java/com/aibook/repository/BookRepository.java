package com.aibook.repository;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 书籍 Repository
 */
@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    /**
     * 根据文件哈希查找书籍
     */
    Optional<Book> findByFileHash(String fileHash);

    /**
     * 根据用户分页查询书籍
     */
    Page<Book> findByUser(User user, Pageable pageable);

    /**
     * 根据用户和格式查询书籍
     */
    Page<Book> findByUserAndFormat(User user, String format, Pageable pageable);

    /**
     * 根据用户和阅读状态查询书籍
     */
    Page<Book> findByUserAndReadingStatus(User user, Book.ReadingStatus status, Pageable pageable);

    /**
     * 根据用户和收藏状态查询书籍
     */
    Page<Book> findByUserAndIsFavorite(User user, Boolean isFavorite, Pageable pageable);

    /**
     * 根据用户和想读状态查询书籍
     */
    Page<Book> findByUserAndIsWanted(User user, Boolean isWanted, Pageable pageable);

    /**
     * 全文搜索书籍
     */
    @Query("SELECT b FROM Book b WHERE b.user = :user AND (" +
           "LOWER(b.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(b.author) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(b.isbn) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(b.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Book> searchByKeyword(@Param("user") User user, @Param("keyword") String keyword, Pageable pageable);

    /**
     * 根据用户和分类查询书籍
     */
    Page<Book> findByUserAndCategoryId(User user, Long categoryId, Pageable pageable);

    /**
     * 根据用户和标签查询书籍
     */
    @Query("SELECT DISTINCT b FROM Book b JOIN b.tags t WHERE b.user = :user AND t.id = :tagId")
    Page<Book> findByUserAndTagId(@Param("user") User user, @Param("tagId") Long tagId, Pageable pageable);

    /**
     * 统计用户书籍数量
     */
    long countByUser(User user);

    /**
     * 根据用户查询所有书籍
     */
    List<Book> findByUser(User user);

    /**
     * 根据用户和文件名查找书籍（title.format）
     */
    @Query("SELECT b FROM Book b WHERE b.user = :user AND (b.title || '.' || b.format) = :filename")
    Optional<Book> findByUserAndFilename(@Param("user") User user, @Param("filename") String filename);
}
