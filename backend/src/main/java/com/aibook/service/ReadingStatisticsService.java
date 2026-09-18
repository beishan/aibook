package com.aibook.service;

import com.aibook.dto.ReadingStatisticsDTO;
import com.aibook.dto.ReadingStatisticsDTO.AuthorStat;
import com.aibook.dto.ReadingStatisticsDTO.BookReadingTimeStat;
import com.aibook.dto.ReadingStatisticsDTO.CategoryStat;
import com.aibook.dto.ReadingStatisticsDTO.FormatStat;
import com.aibook.dto.ReadingStatisticsDTO.MonthReadingStat;
import com.aibook.dto.ReadingStatisticsDTO.Overview;
import com.aibook.dto.ReadingStatisticsDTO.RatingStat;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.ReadingDailyActivityRepository;
import com.aibook.repository.VersionReadingProgressRepository;
import com.aibook.repository.projections.BookStatisticsProjections.BookAuthorCount;
import com.aibook.repository.projections.BookStatisticsProjections.BookCategoryCount;
import com.aibook.repository.projections.BookStatisticsProjections.BookFormatCount;
import com.aibook.repository.projections.BookStatisticsProjections.BookRatingCount;
import com.aibook.repository.projections.BookStatisticsProjections.BookStatusCount;
import com.aibook.repository.projections.BookStatisticsProjections.DailyReadingCount;
import com.aibook.repository.projections.BookStatisticsProjections.MonthlyReadingCount;
import com.aibook.repository.projections.BookStatisticsProjections.VersionReadingTimeProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 阅读统计服务：聚合书籍、阅读进度数据，生成总览、趋势、偏好等多维度统计。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReadingStatisticsService {

    /** 排行榜书籍数上限 */
    private static final int TOP_BOOKS_LIMIT = 10;

    private final BookRepository bookRepository;
    private final VersionReadingProgressRepository versionReadingProgressRepository;
    private final ReadingDailyActivityRepository dailyActivityRepository;

    /**
     * 生成当前用户的阅读统计数据。
     */
    public ReadingStatisticsDTO generateStatistics(User user) {
        ReadingStatisticsDTO.ReadingStatisticsDTOBuilder builder = ReadingStatisticsDTO.builder();

        builder.overview(buildOverview(user))
                .monthlyStats(buildMonthlyStats(user))
                .dailyHeatmap(buildDailyHeatmap(user))
                .ratingDistribution(buildRatingDistribution(user))
                .categoryPreference(buildCategoryPreference(user))
                .authorPreference(buildAuthorPreference(user))
                .formatPreference(buildFormatPreference(user))
                .topReadingTimeBooks(buildTopReadingTimeBooks(user));

        return builder.build();
    }

    // ==================== 总览 ====================

    private Overview buildOverview(User user) {
        List<BookStatusCount> statusCounts = bookRepository.countByReadingStatus(user);
        long finished = 0;
        long reading = 0;
        long unread = 0;
        for (BookStatusCount sc : statusCounts) {
            Book.ReadingStatus status = Book.ReadingStatus.valueOf(sc.getStatus());
            long cnt = safe(sc.getCnt());
            switch (status) {
                case FINISHED -> finished = cnt;
                case READING -> reading = cnt;
                case UNREADING -> unread = cnt;
            }
        }
        long total = finished + reading + unread;
        long wanted = bookRepository.countWanted(user);
        long totalBooks = total + wanted;

        Long totalSeconds = versionReadingProgressRepository.sumReadingTimeSeconds(user);

        long ratedBooks = bookRepository.countRated(user);
        Double avgRating = bookRepository.averageRating(user);

        double completionRate = total == 0 ? 0.0 : (finished * 100.0 / total);

        long averageReadingTime = (totalSeconds != null && total > 0)
                ? totalSeconds / total : 0L;

        return Overview.builder()
                .totalBooks(totalBooks)
                .finishedBooks(finished)
                .readingBooks(reading)
                .unreadBooks(unread)
                .wantedBooks(wanted)
                .completionRate(Math.round(completionRate * 10.0) / 10.0)
                .totalReadingTimeSeconds(totalSeconds != null ? totalSeconds : 0L)
                .averageReadingTimeSeconds(averageReadingTime)
                .averageRating(avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0)
                .ratedBooks(ratedBooks)
                .build();
    }

    // ==================== 月度统计 ====================

    private Map<Integer, MonthReadingStat> buildMonthlyStats(User user) {
        // 返回当前年份 1-12 月的内容，便于前端以固定序列展示
        Map<Integer, MonthReadingStat> result = new HashMap<>();
        int currentYear = LocalDate.now().getYear();
        for (int m = 1; m <= 12; m++) {
            result.put(m, MonthReadingStat.builder()
                    .month(m).activeCount(0).readingTimeSeconds(0L).build());
        }
        // 日记录提供真实时间序列；仓库查询会仅对没有日记录的旧进度使用 lastReadAt 降级。
        for (MonthlyReadingCount row : dailyActivityRepository.countMonthlyReading(user.getId())) {
            if (row.getYear() != null && row.getYear() == currentYear && row.getMonth() != null) {
                result.get(row.getMonth()).setActiveCount(safe(row.getBookCount()));
                result.get(row.getMonth()).setReadingTimeSeconds(
                        row.getTotalReadingSeconds() == null ? 0L : row.getTotalReadingSeconds());
            }
        }
        return result;
    }

    // ==================== 热力图 ====================

    private Map<String, Integer> buildDailyHeatmap(User user) {
        List<DailyReadingCount> rows = dailyActivityRepository.countDailyReading(user.getId());
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Integer> heatmap = new LinkedHashMap<>();
        for (DailyReadingCount row : rows) {
            if (row.getDay() != null) {
                heatmap.put(row.getDay(), safe(row.getBookCount()).intValue());
            }
        }
        return heatmap;
    }

    // ==================== 评分分布 ====================

    private List<RatingStat> buildRatingDistribution(User user) {
        List<BookRatingCount> rows = bookRepository.countByRating(user);
        // 始终展示 1-5 星的完整分布
        Map<Integer, Long> bucket = new HashMap<>();
        for (int i = 1; i <= 5; i++) bucket.put(i, 0L);
        if (rows != null) {
            for (BookRatingCount row : rows) {
                if (row.getRating() != null) {
                    bucket.put(row.getRating(), safe(row.getCnt()));
                }
            }
        }
        return bucket.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> RatingStat.builder().rating(e.getKey()).count(e.getValue()).build())
                .toList();
    }

    // ==================== 分类偏好 ====================

    private List<CategoryStat> buildCategoryPreference(User user) {
        List<BookCategoryCount> rows = bookRepository.countByCategory(user);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        return rows.stream()
                .map(r -> CategoryStat.builder()
                        .categoryId(r.getCategoryId())
                        .categoryName(r.getCategoryName() == null ? "未分类" : r.getCategoryName())
                        .count(safe(r.getCnt()))
                        .build())
                .toList();
    }

    // ==================== 作者偏好 ====================

    private List<AuthorStat> buildAuthorPreference(User user) {
        List<BookAuthorCount> rows = bookRepository.countByAuthor(user, PageRequest.of(0, 20));
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        return rows.stream()
                .map(r -> AuthorStat.builder()
                        .author(r.getAuthor() == null ? "未知作者" : r.getAuthor())
                        .count(safe(r.getCnt()))
                        .build())
                .toList();
    }

    // ==================== 格式偏好 ====================

    private List<FormatStat> buildFormatPreference(User user) {
        List<BookFormatCount> rows = bookRepository.countByFormat(user);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        return rows.stream()
                .map(r -> FormatStat.builder()
                        .format(r.getFormat() == null ? "unknown" : r.getFormat())
                        .displayName(displayFormatName(r.getFormat()))
                        .count(safe(r.getCnt()))
                        .build())
                .toList();
    }

    // ==================== 阅读时长排行 ====================

    private List<BookReadingTimeStat> buildTopReadingTimeBooks(User user) {
        List<VersionReadingTimeProjection> rows = versionReadingProgressRepository
                .findTopReadingTimeBooks(user, PageRequest.of(0, TOP_BOOKS_LIMIT));
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        return rows.stream()
                .map(r -> BookReadingTimeStat.builder()
                        .bookId(r.getBookId())
                        .title(r.getTitle())
                        .author(r.getAuthor())
                        .readingTimeSeconds(r.getTotalReadingSeconds() == null ? 0L : r.getTotalReadingSeconds())
                        .coverUrl(r.getCoverUrl())
                        .build())
                .toList();
    }

    // ==================== 工具方法 ====================

    private static String displayFormatName(String format) {
        if (format == null) return "未知";
        return switch (format.toLowerCase()) {
            case "epub" -> "EPUB";
            case "txt" -> "TXT";
            case "pdf" -> "PDF";
            case "mobi" -> "MOBI";
            case "azw3" -> "AZW3";
            case "docx", "doc" -> "Word";
            case "html", "htm" -> "HTML";
            case "md", "markdown" -> "Markdown";
            case "cbz" -> "CBZ";
            case "cbr" -> "CBR";
            case "structured" -> "在线章节";
            default -> format.toUpperCase();
        };
    }

    private static Long safe(Long value) {
        return value == null ? 0L : value;
    }
}
