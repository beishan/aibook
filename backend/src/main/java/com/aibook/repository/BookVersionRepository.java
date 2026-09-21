package com.aibook.repository;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookVersion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookVersionRepository extends JpaRepository<BookVersion, Long> {

    interface BookVersionCount {
        Long getBookId();

        long getVersionCount();
    }

    List<BookVersion> findByBookOrderByPrimaryVersionDescCreatedAtAsc(Book book);

    Optional<BookVersion> findByBookAndPrimaryVersionTrue(Book book);

    Optional<BookVersion> findByIdAndBook(Long id, Book book);

    Optional<BookVersion> findByFileHash(String fileHash);

    boolean existsByBookAndSourceTypeAndSourceId(
            Book book, String sourceType, String sourceId);

    @Query("SELECT version.book.id AS bookId, COUNT(version) AS versionCount "
            + "FROM BookVersion version WHERE version.book.id IN :bookIds "
            + "GROUP BY version.book.id")
    List<BookVersionCount> countByBookIds(@Param("bookIds") List<Long> bookIds);

    /** 按主键游标分页读取版本路径，供扫描目录来源历史回填使用。 */
    @Query("SELECT version.id AS id, version.book.id AS bookId, version.book.user.id AS userId, "
            + "version.filePath AS filePath FROM BookVersion version "
            + "WHERE version.book.deletedAt IS NULL AND version.id > :afterId ORDER BY version.id")
    List<BookVersionScanSourceBackfillProjection> findScanSourceBackfillCandidatesAfterId(
            @Param("afterId") Long afterId, Pageable pageable);
}
