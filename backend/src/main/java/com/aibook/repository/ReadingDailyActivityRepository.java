package com.aibook.repository;

import com.aibook.model.entity.ReadingDailyActivity;
import com.aibook.repository.projections.BookStatisticsProjections.DailyReadingCount;
import com.aibook.repository.projections.BookStatisticsProjections.MonthlyReadingCount;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReadingDailyActivityRepository extends JpaRepository<ReadingDailyActivity, Long> {

    boolean existsByUserIdAndVersionId(Long userId, Long versionId);

    void deleteByVersionId(Long versionId);

    /** 原子累加当天的实际增量；零秒记录也用于表达打开/翻阅产生的活跃书籍。 */
    @Modifying
    @Query(value = """
            INSERT INTO reading_daily_activity
                (user_id, version_id, reading_date, reading_time_seconds, created_at, updated_at)
            VALUES (:userId, :versionId, :readingDate, :seconds, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (user_id, version_id, reading_date)
            DO UPDATE SET reading_time_seconds = reading_daily_activity.reading_time_seconds
                            + EXCLUDED.reading_time_seconds,
                          updated_at = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    void addReadingTime(
            @Param("userId") Long userId,
            @Param("versionId") Long versionId,
            @Param("readingDate") LocalDate readingDate,
            @Param("seconds") long seconds);

    /**
     * 日记录为真实来源；尚未产生任何日记录的旧进度按 last_read_at 整体回退，避免升级后丢失历史。
     */
    @Query(value = """
            WITH activity AS (
                SELECT rda.reading_date, bv.book_id, rda.reading_time_seconds
                FROM reading_daily_activity rda
                JOIN version_reading_progress vp
                  ON vp.user_id = rda.user_id AND vp.version_id = rda.version_id
                JOIN book_versions bv ON bv.id = rda.version_id
                WHERE rda.user_id = :userId
                UNION ALL
                SELECT CAST(vp.last_read_at AS DATE), bv.book_id, vp.reading_time_seconds
                FROM version_reading_progress vp
                JOIN book_versions bv ON bv.id = vp.version_id
                WHERE vp.user_id = :userId
                  AND vp.last_read_at IS NOT NULL
                  AND (vp.total_progress > 0 OR vp.reading_time_seconds > 0)
                  AND NOT EXISTS (
                      SELECT 1 FROM reading_daily_activity rda
                      WHERE rda.user_id = vp.user_id AND rda.version_id = vp.version_id
                  )
            )
            SELECT CAST(EXTRACT(YEAR FROM reading_date) AS INTEGER) AS year,
                   CAST(EXTRACT(MONTH FROM reading_date) AS INTEGER) AS month,
                   COUNT(DISTINCT book_id) AS bookCount,
                   COALESCE(SUM(reading_time_seconds), 0) AS totalReadingSeconds
            FROM activity
            GROUP BY year, month
            ORDER BY year, month
            """, nativeQuery = true)
    List<MonthlyReadingCount> countMonthlyReading(@Param("userId") Long userId);

    @Query(value = """
            WITH activity AS (
                SELECT rda.reading_date, bv.book_id
                FROM reading_daily_activity rda
                JOIN version_reading_progress vp
                  ON vp.user_id = rda.user_id AND vp.version_id = rda.version_id
                JOIN book_versions bv ON bv.id = rda.version_id
                WHERE rda.user_id = :userId
                UNION ALL
                SELECT CAST(vp.last_read_at AS DATE), bv.book_id
                FROM version_reading_progress vp
                JOIN book_versions bv ON bv.id = vp.version_id
                WHERE vp.user_id = :userId
                  AND vp.last_read_at IS NOT NULL
                  AND (vp.total_progress > 0 OR vp.reading_time_seconds > 0)
                  AND NOT EXISTS (
                      SELECT 1 FROM reading_daily_activity rda
                      WHERE rda.user_id = vp.user_id AND rda.version_id = vp.version_id
                  )
            )
            SELECT TO_CHAR(reading_date, 'YYYY-MM-DD') AS day,
                   COUNT(DISTINCT book_id) AS bookCount
            FROM activity
            WHERE reading_date BETWEEN CURRENT_DATE - INTERVAL '364 days' AND CURRENT_DATE
            GROUP BY reading_date
            ORDER BY reading_date
            """, nativeQuery = true)
    List<DailyReadingCount> countDailyReading(@Param("userId") Long userId);
}
