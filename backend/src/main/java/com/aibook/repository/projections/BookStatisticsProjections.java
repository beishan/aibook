package com.aibook.repository.projections;

/**
 * 阅读统计相关的 JPQL 聚合投影接口。
 * <p>Spring Data JPA 会自动将 @Query 中的构造表达式绑定到这些接口属性上。</p>
 */
public final class BookStatisticsProjections {

    private BookStatisticsProjections() {}

    public interface BookStatusCount {
        String getStatus();
        Long getCnt();
    }

    public interface BookRatingCount {
        Integer getRating();
        Long getCnt();
    }

    public interface BookCategoryCount {
        Long getCategoryId();
        String getCategoryName();
        Long getCnt();
    }

    public interface BookFormatCount {
        String getFormat();
        Long getCnt();
    }

    public interface BookAuthorCount {
        String getAuthor();
        Long getCnt();
    }

    /**
     * 按真实日记录聚合阅读活跃度（旧数据按最后阅读时间降级）。
     */
    public interface MonthlyReadingCount {
        Integer getYear();
        Integer getMonth();
        Long getBookCount();
        Long getTotalReadingSeconds();
    }

    /**
     * 按日聚合阅读活跃度。
     */
    public interface DailyReadingCount {
        String getDay();
        Long getBookCount();
    }

    /**
     * 阅读时长排行投影。
     */
    public interface VersionReadingTimeProjection {
        Long getVersionId();
        Long getBookId();
        String getTitle();
        String getAuthor();
        String getCoverUrl();
        Long getTotalReadingSeconds();
    }
}
