package com.aibook.repository;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookScanSource;
import com.aibook.model.entity.ScanDirectory;
import com.aibook.model.entity.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BookScanSourceRepository extends JpaRepository<BookScanSource, Long> {

    boolean existsByBookAndScanDirectory(Book book, ScanDirectory scanDirectory);

    void deleteByScanDirectory(ScanDirectory scanDirectory);

    List<BookScanSource> findByBook(Book book);

    List<BookScanSource> findByUser(User user);

    /**
     * 以扫描来源为真源统计目录当前关联的有效逻辑书籍。
     * 同一本书有多个文件版本或重复发现记录时只计一次；回收站书籍不计入。
     */
    @Query("SELECT COUNT(DISTINCT source.book.id) FROM BookScanSource source "
            + "WHERE source.scanDirectory = :scanDirectory AND source.user = :user "
            + "AND source.book.user = :user AND source.book.deletedAt IS NULL")
    long countDistinctActiveBooksByScanDirectoryAndUser(
            @Param("scanDirectory") ScanDirectory scanDirectory,
            @Param("user") User user);

    /** 为目录列表批量取得当前有效逻辑书籍数，避免逐目录查询。 */
    @Query("SELECT source.scanDirectory.id AS scanDirectoryId, "
            + "COUNT(DISTINCT source.book.id) AS bookCount FROM BookScanSource source "
            + "WHERE source.scanDirectory.user = :user AND source.user = :user "
            + "AND source.book.user = :user AND source.book.deletedAt IS NULL "
            + "GROUP BY source.scanDirectory.id")
    List<ScanDirectoryBookCountProjection> countDistinctActiveBooksByDirectoryAndUser(
            @Param("user") User user);

    @Query("SELECT source.book.id AS bookId, source.scanDirectory.id AS scanDirectoryId "
            + "FROM BookScanSource source WHERE source.book.id IN :bookIds")
    List<BookScanSourceKeyProjection> findKeysByBookIds(
            @Param("bookIds") List<Long> bookIds);
}
