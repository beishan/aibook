package com.aibook.repository;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.Category;
import com.aibook.model.entity.Tag;
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

    Optional<Book> findByFileHashAndDeletedAtIsNull(String fileHash);

    Optional<Book> findByIdAndUserAndDeletedAtIsNull(Long id, User user);

    Optional<Book> findByIdAndUserAndDeletedAtIsNotNullAndPurgedAtIsNull(Long id, User user);

    /**
     * 根据用户分页查询书籍
     */
    Page<Book> findByUserAndDeletedAtIsNull(User user, Pageable pageable);

    /**
     * 根据用户和格式查询书籍
     */
    Page<Book> findByUserAndFormatAndDeletedAtIsNull(
            User user, String format, Pageable pageable);

    /**
     * 根据用户和阅读状态查询书籍
     */
    Page<Book> findByUserAndReadingStatusAndDeletedAtIsNull(
            User user, Book.ReadingStatus status, Pageable pageable);

    /**
     * 根据用户和收藏状态查询书籍
     */
    Page<Book> findByUserAndIsFavoriteAndDeletedAtIsNull(
            User user, Boolean isFavorite, Pageable pageable);

    /**
     * 根据用户和想读状态查询书籍
     */
    Page<Book> findByUserAndIsWantedAndDeletedAtIsNull(
            User user, Boolean isWanted, Pageable pageable);

    /**
     * 全文搜索书籍
     */
    @Query("SELECT b FROM Book b WHERE b.user = :user AND b.deletedAt IS NULL AND (" +
           "LOWER(b.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(b.author) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(b.isbn) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(b.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Book> searchByKeyword(@Param("user") User user, @Param("keyword") String keyword, Pageable pageable);

    /**
     * 根据用户和分类查询书籍
     */
    Page<Book> findByUserAndCategoryIdAndDeletedAtIsNull(
            User user, Long categoryId, Pageable pageable);

    /**
     * 根据多个分类查询书籍（包含子分类筛选）。
     */
    Page<Book> findByUserAndCategoryIdInAndDeletedAtIsNull(
            User user, List<Long> categoryIds, Pageable pageable);

    /**
     * 获取某分类下的书籍，用于分类删除和合并。
     */
    List<Book> findByUserAndCategoryAndDeletedAtIsNull(User user, Category category);

    /**
     * 统计某分类下的书籍。
     */
    long countByUserAndCategoryAndDeletedAtIsNull(User user, Category category);

    /**
     * 统计使用指定标签的书籍。
     */
    long countByUserAndTagsContainingAndDeletedAtIsNull(User user, Tag tag);

    /**
     * 根据用户和标签查询书籍
     */
    @Query("SELECT DISTINCT b FROM Book b JOIN b.tags t " +
            "WHERE b.user = :user AND b.deletedAt IS NULL AND t.id = :tagId")
    Page<Book> findByUserAndTagId(@Param("user") User user, @Param("tagId") Long tagId, Pageable pageable);

    /**
     * 统计用户书籍数量
     */
    long countByUserAndDeletedAtIsNull(User user);

    /**
     * 根据用户查询所有书籍
     */
    List<Book> findByUserAndDeletedAtIsNull(User user);

    @Query("""
            SELECT b.id AS id,
                   b.title AS title,
                   b.author AS author,
                   b.isbn AS isbn,
                   b.filePath AS filePath
            FROM Book b
            WHERE b.user.id = :userId AND b.deletedAt IS NULL
            ORDER BY b.id
            """)
    List<BookVersionIdentityProjection> findVersionIdentitiesByUserId(
            @Param("userId") Long userId);

    /**
     * 根据用户和文件名查找书籍（title.format）
     */
    @Query("SELECT b FROM Book b WHERE b.user = :user AND b.deletedAt IS NULL " +
            "AND (b.title || '.' || b.format) = :filename")
    Optional<Book> findByUserAndFilename(@Param("user") User user, @Param("filename") String filename);

    /**
     * 查找作者或描述为空的书籍（用于刮削）
     */
    @Query("SELECT b FROM Book b WHERE b.deletedAt IS NULL " +
            "AND (b.author IS NULL OR b.description IS NULL)")
    List<Book> findByAuthorIsNullOrDescriptionIsNull();

    /**
     * 查找封面为空或以http开头的书籍（用于下载封面）
     */
    @Query("SELECT b FROM Book b WHERE b.deletedAt IS NULL " +
            "AND (b.coverUrl IS NULL OR b.coverUrl LIKE :prefix%)")
    List<Book> findByCoverUrlIsNullOrCoverUrlStartingWith(@Param("prefix") String prefix);

    /**
     * 根据ID列表和用户查询书籍（用于批量操作）
     */
    @Query("SELECT b FROM Book b WHERE b.id IN :ids AND b.user = :user " +
            "AND b.deletedAt IS NULL")
    List<Book> findByIdInAndUser(@Param("ids") List<Long> ids, @Param("user") User user);

    /**
     * 根据用户查找作者或描述为空的书籍（用于批量刮削）
     */
    @Query("SELECT b FROM Book b WHERE b.user = :user AND b.deletedAt IS NULL " +
            "AND (b.author IS NULL OR b.description IS NULL)")
    List<Book> findByUserAndAuthorIsNullOrDescriptionIsNull(@Param("user") User user);

    @Query("SELECT b FROM Book b WHERE b.user = :user AND b.deletedAt IS NOT NULL " +
            "AND b.purgedAt IS NULL " +
            "AND (:keyword = '' OR LOWER(b.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(b.author) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(b.filePath) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Book> findTrash(
            @Param("user") User user,
            @Param("keyword") String keyword,
            Pageable pageable);

    List<Book> findByUserAndDeletedAtIsNotNullAndPurgedAtIsNull(User user);

    @Query("SELECT b FROM Book b WHERE b.id IN :ids AND b.user = :user " +
            "AND b.deletedAt IS NOT NULL AND b.purgedAt IS NULL")
    List<Book> findTrashByIds(
            @Param("ids") List<Long> ids,
            @Param("user") User user);

    long countByUserAndDeletedAtIsNotNullAndPurgedAtIsNull(User user);
}
