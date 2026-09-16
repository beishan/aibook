package com.aibook.service;

import com.aibook.dto.ReadingStatisticsDTO;
import com.aibook.dto.ReadingStatisticsDTO.MonthReadingStat;
import com.aibook.dto.ReadingStatisticsDTO.RatingStat;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.VersionReadingProgressRepository;
import com.aibook.repository.projections.BookStatisticsProjections.BookAuthorCount;
import com.aibook.repository.projections.BookStatisticsProjections.BookCategoryCount;
import com.aibook.repository.projections.BookStatisticsProjections.BookFormatCount;
import com.aibook.repository.projections.BookStatisticsProjections.BookRatingCount;
import com.aibook.repository.projections.BookStatisticsProjections.BookStatusCount;
import com.aibook.repository.projections.BookStatisticsProjections.DailyReadingCount;
import com.aibook.repository.projections.BookStatisticsProjections.MonthlyReadingCount;
import com.aibook.repository.projections.BookStatisticsProjections.VersionReadingTimeProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadingStatisticsServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private VersionReadingProgressRepository versionReadingProgressRepository;

    @InjectMocks
    private ReadingStatisticsService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("testuser");
    }

    @Test
    void 应汇总总览统计数据() {
        when(bookRepository.countByReadingStatus(user)).thenReturn(List.of(
                statusCount("FINISHED", 10L),
                statusCount("READING", 3L),
                statusCount("UNREADING", 7L)
        ));
        when(bookRepository.countWanted(user)).thenReturn(2L);
        when(versionReadingProgressRepository.sumReadingTimeSeconds(user)).thenReturn(36_000L);
        when(bookRepository.countRated(user)).thenReturn(9L);
        when(bookRepository.averageRating(user)).thenReturn(4.2);
        when(versionReadingProgressRepository.countMonthlyReading(user.getId())).thenReturn(List.of());
        when(versionReadingProgressRepository.countDailyReading(user.getId())).thenReturn(List.of());
        when(bookRepository.countByRating(user)).thenReturn(List.of());
        when(bookRepository.countByCategory(user)).thenReturn(List.of());
        when(bookRepository.countByAuthor(user, PageRequest.of(0, 20))).thenReturn(List.of());
        when(bookRepository.countByFormat(user)).thenReturn(List.of());
        when(versionReadingProgressRepository.findTopReadingTimeBooks(user, PageRequest.of(0, 10)))
                .thenReturn(List.of());

        ReadingStatisticsDTO dto = service.generateStatistics(user);

        assertThat(dto.getOverview()).isNotNull();
        assertThat(dto.getOverview().getTotalBooks()).isEqualTo(22); // 10+3+7+2
        assertThat(dto.getOverview().getFinishedBooks()).isEqualTo(10);
        assertThat(dto.getOverview().getReadingBooks()).isEqualTo(3);
        assertThat(dto.getOverview().getUnreadBooks()).isEqualTo(7);
        assertThat(dto.getOverview().getWantedBooks()).isEqualTo(2);
        assertThat(dto.getOverview().getCompletionRate()).isEqualTo(50.0); // 10 / 20
        assertThat(dto.getOverview().getTotalReadingTimeSeconds()).isEqualTo(36_000);
        assertThat(dto.getOverview().getAverageRating()).isEqualTo(4.2);
        assertThat(dto.getOverview().getRatedBooks()).isEqualTo(9);
    }

    @Test
    void 无数据时总览应为零值而非异常() {
        when(bookRepository.countByReadingStatus(user)).thenReturn(List.of());
        when(bookRepository.countWanted(user)).thenReturn(0L);
        when(versionReadingProgressRepository.sumReadingTimeSeconds(user)).thenReturn(0L);
        when(bookRepository.countRated(user)).thenReturn(0L);
        when(bookRepository.averageRating(user)).thenReturn(null);

        ReadingStatisticsDTO dto = service.generateStatistics(user);

        assertThat(dto.getOverview().getTotalBooks()).isZero();
        assertThat(dto.getOverview().getCompletionRate()).isEqualTo(0.0);
    }

    @Test
    void 返回完整评分分布五档() {
        when(bookRepository.countByRating(user)).thenReturn(List.of(
                ratingCount(4, 5L),
                ratingCount(5, 3L)
        ));

        ReadingStatisticsDTO dto = service.generateStatistics(user);
        List<RatingStat> distribution = dto.getRatingDistribution();

        assertThat(distribution).hasSize(5);
        assertThat(distribution.get(0).getRating()).isEqualTo(1);
        assertThat(distribution.get(0).getCount()).isZero();
        assertThat(distribution.get(3).getRating()).isEqualTo(4);
        assertThat(distribution.get(3).getCount()).isEqualTo(5);
        assertThat(distribution.get(4).getRating()).isEqualTo(5);
        assertThat(distribution.get(4).getCount()).isEqualTo(3);
    }

    @Test
    void 月度统计应包含当前年全部12个月() {
        when(versionReadingProgressRepository.countMonthlyReading(user.getId())).thenReturn(List.of(
                monthlyCount(2026, 1, 3L, 7200L),
                monthlyCount(2026, 3, 5L, 10800L)
        ));

        ReadingStatisticsDTO dto = service.generateStatistics(user);
        Map<Integer, MonthReadingStat> monthly = dto.getMonthlyStats();

        assertThat(monthly).hasSize(12);
        // 阅读活跃本数与时长均来自阅读进度统计（lastReadAt 归属月份）
        assertThat(monthly.get(1).getActiveCount()).isEqualTo(3);
        assertThat(monthly.get(1).getReadingTimeSeconds()).isEqualTo(7200);
        assertThat(monthly.get(3).getActiveCount()).isEqualTo(5);
        assertThat(monthly.get(2).getActiveCount()).isZero();
    }

    @Test
    void 热力图应返回日度聚合() {
        when(versionReadingProgressRepository.countDailyReading(user.getId())).thenReturn(List.of(
                dailyCount("2026-09-01", 2L),
                dailyCount("2026-09-02", 1L)
        ));

        ReadingStatisticsDTO dto = service.generateStatistics(user);

        assertThat(dto.getDailyHeatmap()).containsEntry("2026-09-01", 2);
        assertThat(dto.getDailyHeatmap()).containsEntry("2026-09-02", 1);
    }

    @Test
    void 偏好与排行榜应透传投影结果() {
        when(bookRepository.countByCategory(user)).thenReturn(List.of(
                categoryCount(1L, "小说", 8L)
        ));
        when(bookRepository.countByAuthor(user, PageRequest.of(0, 20))).thenReturn(List.of(
                authorCount("金庸", 4L)
        ));
        when(bookRepository.countByFormat(user)).thenReturn(List.of(
                formatCount("epub", 12L)
        ));
        when(versionReadingProgressRepository.findTopReadingTimeBooks(user, PageRequest.of(0, 10)))
                .thenReturn(List.of(readingTime(101L, "射雕英雄传", "金庸", 7200L)));

        ReadingStatisticsDTO dto = service.generateStatistics(user);

        assertThat(dto.getCategoryPreference()).hasSize(1);
        assertThat(dto.getCategoryPreference().get(0).getCategoryName()).isEqualTo("小说");
        assertThat(dto.getAuthorPreference()).hasSize(1);
        assertThat(dto.getAuthorPreference().get(0).getAuthor()).isEqualTo("金庸");
        assertThat(dto.getFormatPreference().get(0).getDisplayName()).isEqualTo("EPUB");
        assertThat(dto.getTopReadingTimeBooks()).hasSize(1);
        assertThat(dto.getTopReadingTimeBooks().get(0).getReadingTimeSeconds()).isEqualTo(7200);
    }

    // ==================== 构造简易投影的工厂方法 ====================

    private static BookStatusCount statusCount(String status, long cnt) {
        return new BookStatusCount() {
            @Override public String getStatus() { return status; }
            @Override public Long getCnt() { return cnt; }
        };
    }

    private static BookRatingCount ratingCount(int rating, long cnt) {
        return new BookRatingCount() {
            @Override public Integer getRating() { return rating; }
            @Override public Long getCnt() { return cnt; }
        };
    }

    private static BookCategoryCount categoryCount(Long id, String name, long cnt) {
        return new BookCategoryCount() {
            @Override public Long getCategoryId() { return id; }
            @Override public String getCategoryName() { return name; }
            @Override public Long getCnt() { return cnt; }
        };
    }

    private static BookAuthorCount authorCount(String author, long cnt) {
        return new BookAuthorCount() {
            @Override public String getAuthor() { return author; }
            @Override public Long getCnt() { return cnt; }
        };
    }

    private static BookFormatCount formatCount(String format, long cnt) {
        return new BookFormatCount() {
            @Override public String getFormat() { return format; }
            @Override public Long getCnt() { return cnt; }
        };
    }

    private static MonthlyReadingCount monthlyCount(int year, int month, long books, long seconds) {
        return new MonthlyReadingCount() {
            @Override public Integer getYear() { return year; }
            @Override public Integer getMonth() { return month; }
            @Override public Long getBookCount() { return books; }
            @Override public Long getTotalReadingSeconds() { return seconds; }
        };
    }

    private static DailyReadingCount dailyCount(String day, long books) {
        return new DailyReadingCount() {
            @Override public String getDay() { return day; }
            @Override public Long getBookCount() { return books; }
        };
    }

    private static VersionReadingTimeProjection readingTime(
            Long bookId, String title, String author, Long seconds) {
        return new VersionReadingTimeProjection() {
            @Override public Long getVersionId() { return 1L; }
            @Override public Long getBookId() { return bookId; }
            @Override public String getTitle() { return title; }
            @Override public String getAuthor() { return author; }
            @Override public String getCoverUrl() { return null; }
            @Override public Long getTotalReadingSeconds() { return seconds; }
        };
    }
}
