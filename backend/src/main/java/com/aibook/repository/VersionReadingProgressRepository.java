package com.aibook.repository;

import com.aibook.model.entity.BookVersion;
import com.aibook.model.entity.User;
import com.aibook.model.entity.VersionReadingProgress;
import com.aibook.repository.projections.BookStatisticsProjections.DailyReadingCount;
import com.aibook.repository.projections.BookStatisticsProjections.MonthlyReadingCount;
import com.aibook.repository.projections.BookStatisticsProjections.VersionReadingTimeProjection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VersionReadingProgressRepository
        extends JpaRepository<VersionReadingProgress, Long> {
    Optional<VersionReadingProgress> findByUserAndVersion(User user, BookVersion version);

    boolean existsByUserAndVersion(User user, BookVersion version);

    void deleteByVersion(BookVersion version);

    // ==================== 阅读统计查询 ====================

    /**
     * 按年/月聚合阅读活跃度：统计每个年月组合中被阅读过（非零进度或有时长）的不同书籍数与阅读秒数合计。
     * <p>原生查询用 CAST + EXTRACT 取年月，避免 Hibernate 将 PostgreSQL 的 :: 转换误判为参数占位符。</p>
     */
    @Query(value = """
            SELECT CAST(EXTRACT(YEAR FROM vp.last_read_at)    AS INTEGER) AS year,
                   CAST(EXTRACT(MONTH FROM vp.last_read_at)   AS INTEGER) AS month,
                   COUNT(DISTINCT vp.version_id)              AS bookCount,
                   SUM(vp.reading_time_seconds)                AS totalReadingSeconds
            FROM version_reading_progress vp
            WHERE vp.user_id = :userId AND vp.last_read_at IS NOT NULL
              AND (vp.total_progress > 0 OR vp.reading_time_seconds > 0)
            GROUP BY year, month
            ORDER BY year, month
            """, nativeQuery = true)
    List<MonthlyReadingCount> countMonthlyReading(@Param("userId") Long userId);

    /**
     * 按日聚合阅读活跃度：统计每日被阅读过的不同书籍数（最近 365 天）。
     */
    @Query(value = """
            SELECT TO_CHAR(vp.last_read_at, 'YYYY-MM-DD') AS day,
                   COUNT(DISTINCT vp.version_id)           AS bookCount
            FROM version_reading_progress vp
            WHERE vp.user_id = :userId AND vp.last_read_at IS NOT NULL
              AND vp.last_read_at >= CURRENT_DATE - INTERVAL '365 days'
              AND (vp.total_progress > 0 OR vp.reading_time_seconds > 0)
            GROUP BY day
            ORDER BY day
            """, nativeQuery = true)
    List<DailyReadingCount> countDailyReading(@Param("userId") Long userId);

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
