package com.aibook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 阅读统计数据聚合 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadingStatisticsDTO {

    /** 总览统计 */
    private Overview overview;

    /** 当前年份各月阅读数据，key 为月份 1-12 */
    private Map<Integer, MonthReadingStat> monthlyStats;

    /** 月度阅读热力图数据：key 为 "YYYY-MM-DD"，value 为该日读完/阅读的书籍数量 */
    private Map<String, Integer> dailyHeatmap;

    /** 评分分布 */
    private List<RatingStat> ratingDistribution;

    /** 分类偏好 */
    private List<CategoryStat> categoryPreference;

    /** 作者偏好（前 20） */
    private List<AuthorStat> authorPreference;

    /** 格式偏好 */
    private List<FormatStat> formatPreference;

    /** 阅读时长排行榜（前 10） */
    private List<BookReadingTimeStat> topReadingTimeBooks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Overview {
        /** 书籍总数 */
        private long totalBooks;
        /** 已读完数 */
        private long finishedBooks;
        /** 正在阅读数 */
        private long readingBooks;
        /** 未读 */
        private long unreadBooks;
        /** 想读 */
        private long wantedBooks;
        /** 完成率 (0.0 ~ 100.0) */
        private double completionRate;
        /** 累计阅读时长（秒） */
        private long totalReadingTimeSeconds;
        /** 平均阅读时长（秒/本，仅计有阅读时长的书） */
        private long averageReadingTimeSeconds;
        /** 平均评分 */
        private double averageRating;
        /** 已打分的书籍数 */
        private long ratedBooks;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthReadingStat {
        /** 月份 1-12 */
        private int month;
        /** 当月阅读活跃的不同书籍数（以最后阅读时间归属月份） */
        private long activeCount;
        /** 当月阅读时长（秒） */
        private long readingTimeSeconds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RatingStat {
        private int rating;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryStat {
        private Long categoryId;
        private String categoryName;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthorStat {
        private String author;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FormatStat {
        private String format;
        private long count;
        /** 友好格式名称 */
        private String displayName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookReadingTimeStat {
        private Long bookId;
        private String title;
        private String author;
        private long readingTimeSeconds;
        private String coverUrl;
    }
}
