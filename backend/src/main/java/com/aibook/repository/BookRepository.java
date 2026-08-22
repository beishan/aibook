package com.aibook.repository;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.Category;
import com.aibook.model.entity.Tag;
import com.aibook.model.entity.User;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 书籍 Repository
 */
@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    String LIBRARY_VISIBLE = " (NOT EXISTS (SELECT source FROM BookScanSource source "
            + "WHERE source.book = b) OR EXISTS (SELECT source FROM BookScanSource source "
            + "WHERE source.book = b AND (source.scanDirectory.libraryVisible = true "
            + "OR source.scanDirectory.libraryVisible IS NULL))) ";

    /**
     * 根据文件哈希查找书籍
     */
    Optional<Book> findByFileHash(String fileHash);

    Optional<Book> findByFileHashAndDeletedAtIsNull(String fileHash);

    Optional<Book> findByIdAndUserAndDeletedAtIsNull(Long id, User user);

    Optional<Book> findByIdAndUserAndDeletedAtIsNotNullAndPurgedAtIsNull(Long id, User user);

    /** 按主键游标分页读取来源回填需要的最小字段。 */
    @Query("SELECT b.id AS id, b.user.id AS userId, b.filePath AS filePath "
            + "FROM Book b WHERE b.id > :afterId ORDER BY b.id")
    List<BookScanSourceBackfillProjection> findBackfillCandidatesAfterId(
            @Param("afterId") Long afterId, Pageable pageable);

    /** 按主键游标分页读取尚未标记首次入库方式的历史书籍。 */
    @Query("SELECT b.id AS id, b.filePath AS filePath FROM Book b "
            + "WHERE b.sourceType IS NULL AND b.id > :afterId ORDER BY b.id")
    List<BookSourceTypeBackfillProjection> findSourceTypeBackfillCandidatesAfterId(
            @Param("afterId") Long afterId, Pageable pageable);

    /**
     * 根据用户分页查询书籍
     */
    @Query("SELECT b FROM Book b WHERE b.user = :user AND b.deletedAt IS NULL AND" + LIBRARY_VISIBLE)
    Page<Book> findByUserAndDeletedAtIsNull(@Param("user") User user, Pageable pageable);

    /**
     * 根据用户和格式查询书籍
     */
    @Query("SELECT b FROM Book b WHERE b.user = :user AND b.format = :format "
            + "AND b.deletedAt IS NULL AND" + LIBRARY_VISIBLE)
    Page<Book> findByUserAndFormatAndDeletedAtIsNull(
            @Param("user") User user, @Param("format") String format, Pageable pageable);

    /**
     * 根据用户和阅读状态查询书籍
     */
    @Query("SELECT b FROM Book b WHERE b.user = :user AND b.readingStatus = :status "
            + "AND b.deletedAt IS NULL AND" + LIBRARY_VISIBLE)
    Page<Book> findByUserAndReadingStatusAndDeletedAtIsNull(
            @Param("user") User user, @Param("status") Book.ReadingStatus status, Pageable pageable);

    /**
     * 根据用户和收藏状态查询书籍
     */
    @Query("SELECT b FROM Book b WHERE b.user = :user AND b.isFavorite = :isFavorite "
            + "AND b.deletedAt IS NULL AND" + LIBRARY_VISIBLE)
    Page<Book> findByUserAndIsFavoriteAndDeletedAtIsNull(
            @Param("user") User user, @Param("isFavorite") Boolean isFavorite, Pageable pageable);

    /**
     * 根据用户和想读状态查询书籍
     */
    @Query("SELECT b FROM Book b WHERE b.user = :user AND b.isWanted = :isWanted "
            + "AND b.deletedAt IS NULL AND" + LIBRARY_VISIBLE)
    Page<Book> findByUserAndIsWantedAndDeletedAtIsNull(
            @Param("user") User user, @Param("isWanted") Boolean isWanted, Pageable pageable);

    /** 获取用户书架中的全部可见书籍，服务层再按分组组织。 */
    @Query("SELECT b FROM Book b WHERE b.user = :user AND b.onShelf = true "
            + "AND b.deletedAt IS NULL AND" + LIBRARY_VISIBLE
            + " ORDER BY b.shelfSortOrder ASC, b.shelfAddedAt DESC")
    List<Book> findShelfBooks(@Param("user") User user);

    /** 分组删除时必须包含回收站及书库隐藏书籍，避免遗留外键引用。 */
    List<Book> findByUserAndShelfGroup(User user, com.aibook.model.entity.ShelfGroup shelfGroup);

    /** 分组删除时计算未分组顺序，包含当前用户全部书架关联。 */
    List<Book> findByUserAndOnShelfTrue(User user);

    /**
     * 全文搜索书籍
     */
    @Query("SELECT b FROM Book b WHERE b.user = :user AND b.deletedAt IS NULL AND (" +
           "LOWER(b.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(b.author) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(b.isbn) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(b.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND" + LIBRARY_VISIBLE)
    Page<Book> searchByKeyword(@Param("user") User user, @Param("keyword") String keyword, Pageable pageable);

    /**
     * 根据用户和分类查询书籍
     */
    @Query("SELECT b FROM Book b WHERE b.user = :user AND b.category.id = :categoryId "
            + "AND b.deletedAt IS NULL AND" + LIBRARY_VISIBLE)
    Page<Book> findByUserAndCategoryIdAndDeletedAtIsNull(
            @Param("user") User user, @Param("categoryId") Long categoryId, Pageable pageable);

    /**
     * 根据多个分类查询书籍（包含子分类筛选）。
     */
    @Query("SELECT b FROM Book b WHERE b.user = :user AND b.category.id IN :categoryIds "
            + "AND b.deletedAt IS NULL AND" + LIBRARY_VISIBLE)
    Page<Book> findByUserAndCategoryIdInAndDeletedAtIsNull(
            @Param("user") User user, @Param("categoryIds") List<Long> categoryIds, Pageable pageable);

    /**
     * 获取某分类下的书籍，用于分类删除和合并。
     */
    @Query("SELECT b FROM Book b WHERE b.user = :user AND b.category = :category "
            + "AND b.deletedAt IS NULL")
    List<Book> findByUserAndCategoryAndDeletedAtIsNull(
            @Param("user") User user, @Param("category") Category category);

    /**
     * 统计某分类下的书籍。
     */
    @Query("SELECT COUNT(b) FROM Book b WHERE b.user = :user AND b.category = :category "
            + "AND b.deletedAt IS NULL AND" + LIBRARY_VISIBLE)
    long countByUserAndCategoryAndDeletedAtIsNull(
            @Param("user") User user, @Param("category") Category category);

    /**
     * 统计使用指定标签的书籍。
     */
    @Query("SELECT COUNT(DISTINCT b) FROM Book b JOIN b.tags t WHERE b.user = :user AND t = :tag "
            + "AND b.deletedAt IS NULL AND" + LIBRARY_VISIBLE)
    long countByUserAndTagsContainingAndDeletedAtIsNull(
            @Param("user") User user, @Param("tag") Tag tag);

    /**
     * 根据用户和标签查询书籍
     */
    @Query("SELECT DISTINCT b FROM Book b JOIN b.tags t " +
            "WHERE b.user = :user AND b.deletedAt IS NULL AND t.id = :tagId AND" + LIBRARY_VISIBLE)
    Page<Book> findByUserAndTagId(@Param("user") User user, @Param("tagId") Long tagId, Pageable pageable);

    /**
     * 统计用户书籍数量
     */
    @Query("SELECT COUNT(b) FROM Book b WHERE b.user = :user AND b.deletedAt IS NULL AND" + LIBRARY_VISIBLE)
    long countByUserAndDeletedAtIsNull(@Param("user") User user);

    /**
     * 根据用户查询所有书籍
     */
    @Query("SELECT b FROM Book b WHERE b.user = :user AND b.deletedAt IS NULL AND" + LIBRARY_VISIBLE)
    List<Book> findByUserAndDeletedAtIsNull(@Param("user") User user);

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
     * 根据用户和文件名查找书籍（title.format），用于同步等已知书籍操作。
     */
    @Query("SELECT b FROM Book b WHERE b.user = :user AND b.deletedAt IS NULL " +
            "AND (b.title || '.' || b.format) = :filename")
    Optional<Book> findByUserAndFilename(@Param("user") User user, @Param("filename") String filename);

    /**
     * 根据用户和文件名查找书库中可见的书籍，用于 WebDAV 等发现入口。
     */
    @Query("SELECT b FROM Book b WHERE b.user = :user AND b.deletedAt IS NULL " +
            "AND (b.title || '.' || b.format) = :filename AND" + LIBRARY_VISIBLE)
    Optional<Book> findVisibleByUserAndFilename(
            @Param("user") User user, @Param("filename") String filename);

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

    List<Book> findByUserAndDeletedAtBeforeAndPurgedAtIsNull(
            User user, LocalDateTime deletedBefore);

    @Query("SELECT b FROM Book b WHERE b.id IN :ids AND b.user = :user " +
            "AND b.deletedAt IS NOT NULL AND b.purgedAt IS NULL")
    List<Book> findTrashByIds(
            @Param("ids") List<Long> ids,
            @Param("user") User user);

    long countByUserAndDeletedAtIsNotNullAndPurgedAtIsNull(User user);
}
