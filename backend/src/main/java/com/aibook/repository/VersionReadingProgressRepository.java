package com.aibook.repository;

import com.aibook.model.entity.BookVersion;
import com.aibook.model.entity.User;
import com.aibook.model.entity.VersionReadingProgress;
import com.aibook.repository.projections.BookStatisticsProjections.VersionReadingTimeProjection;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VersionReadingProgressRepository
        extends JpaRepository<VersionReadingProgress, Long> {
    Optional<VersionReadingProgress> findByUserAndVersion(User user, BookVersion version);

    /** 串行化同一账户、版本的心跳差值计算，防止并发请求重复累计或覆盖。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT vp FROM VersionReadingProgress vp WHERE vp.user = :user AND vp.version = :version")
    Optional<VersionReadingProgress> findByUserAndVersionForUpdate(
            @Param("user") User user, @Param("version") BookVersion version);

    boolean existsByUserAndVersion(User user, BookVersion version);

    void deleteByVersion(BookVersion version);

    /**
     * 阅读时长排行（前 N 本）：按阅读秒数倒序。
     * 统计不受书库"可见性"开关影响，用户自己的阅读数据应当全部呈现。
     */
    @Query("""
            SELECT v.id AS versionId, b.id AS bookId, b.title AS title,
                   b.author AS author, b.coverUrl AS coverUrl,
                   SUM(vp.readingTimeSeconds) AS totalReadingSeconds
            FROM VersionReadingProgress vp
            JOIN vp.version v JOIN v.book b
            WHERE vp.user = :user
              AND b.deletedAt IS NULL
              AND vp.readingTimeSeconds > 0
            GROUP BY v.id, b.id, b.title, b.author, b.coverUrl
            ORDER BY totalReadingSeconds DESC
            """)
    List<VersionReadingTimeProjection> findTopReadingTimeBooks(
            @Param("user") User user, Pageable pageable);

    /** 用户累计阅读总秒数。 */
    @Query("""
            SELECT COALESCE(SUM(vp.readingTimeSeconds), 0)
            FROM VersionReadingProgress vp
            WHERE vp.user = :user AND vp.readingTimeSeconds > 0
            """)
    Long sumReadingTimeSeconds(@Param("user") User user);
}
