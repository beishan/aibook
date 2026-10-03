package com.aibook.service.crawler;

import com.aibook.dto.crawler.CrawlerCategoryCleanupDtos.Request;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.CrawlerBookRepository;
import com.aibook.repository.projections.CrawlerCategoryCleanupCandidate;
import com.aibook.util.CrawlerCategoryPolicy;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CrawlerCategoryCleanupServiceTest {
    private final User user = User.builder().id(7L).build();
    private final CrawlerBookRepository crawlerBooks = mock(CrawlerBookRepository.class);
    private final BookRepository libraryBooks = mock(BookRepository.class);
    private final CrawlerCategoryCleanupService service = new CrawlerCategoryCleanupService(crawlerBooks, libraryBooks);

    @Test
    void previewScansMultiplePagesAndCapsSamplesWithoutTruncatingTotal() {
        var firstPage = LongStream.rangeClosed(1, 500)
                .mapToObj(id -> candidate(id, "示例书 " + id, "《示例书 " + id + "》", null)).toList();
        var last = candidate(501L, "最后一本", "最后一本", null);
        when(crawlerBooks.findCategoryCleanupCandidates(eq(user), eq(2L), eq(0L), any(Pageable.class)))
                .thenReturn(firstPage);
        when(crawlerBooks.findCategoryCleanupCandidates(eq(user), eq(2L), eq(500L), any(Pageable.class)))
                .thenReturn(List.of(last));
        when(crawlerBooks.findCategoryCleanupCandidates(eq(user), eq(2L), eq(501L), any(Pageable.class)))
                .thenReturn(List.of());

        var result = service.preview(user, new Request(2L, List.of(), true));

        assertThat(result.matchedBooks()).isEqualTo(501);
        assertThat(result.samples()).hasSize(100);
        verifyNoInteractions(libraryBooks);
        verify(crawlerBooks, never()).clearCategoryIfUnchanged(any(), any(), any());
    }

    @Test
    void clearsMatchedCategoriesButPreservesManualLibraryChangesAndNormalCategories() {
        var titleMatch = candidate(1L, "示例书", "《示例书》", "示例书");
        var manualLibrary = candidate(2L, "另一书", "另一书", "文学");
        var explicitName = candidate(3L, "第三书", "错误分类", null);
        var normal = candidate(4L, "正常书", "科幻", null);
        when(crawlerBooks.findCategoryCleanupCandidates(eq(user), isNull(), eq(0L), any(Pageable.class)))
                .thenReturn(List.of(titleMatch, manualLibrary, explicitName, normal));
        when(crawlerBooks.clearCategoryIfUnchanged(anyLong(), eq(user), anyString())).thenReturn(1);
        when(libraryBooks.clearCrawlerCategoryIfUnchanged(101L, user, 201L)).thenReturn(1);

        var result = service.cleanup(user, new Request(null, List.of("错误分类"), true));

        assertThat(result.clearedBooks()).isEqualTo(3);
        assertThat(result.clearedLibraryBooks()).isEqualTo(1);
        verify(crawlerBooks, never()).clearCategoryIfUnchanged(eq(4L), any(), any());
        verify(libraryBooks, never()).clearCrawlerCategoryIfUnchanged(eq(102L), any(), any());
    }

    @Test
    void skipsChangedRecordsAndHonorsDisablingLibraryCleanup() {
        var changed = candidate(1L, "示例书", "示例书", "示例书");
        var retained = candidate(2L, "另一书", "另一书", "另一书");
        when(crawlerBooks.findCategoryCleanupCandidates(eq(user), isNull(), eq(0L), any(Pageable.class)))
                .thenReturn(List.of(changed, retained));
        when(crawlerBooks.clearCategoryIfUnchanged(2L, user, "另一书")).thenReturn(1);

        var result = service.cleanup(user, new Request(null, List.of(), false));

        assertThat(result.clearedBooks()).isEqualTo(1);
        assertThat(result.skippedBooks()).isEqualTo(1);
        verifyNoInteractions(libraryBooks);
    }

    @Test
    void recognizesBookTitleFormattingWithoutMatchingPartialTitlesOrGenres() {
        assertThat(CrawlerCategoryPolicy.isBookTitle("示例 书名", "《示例书名》")).isTrue();
        assertThat(CrawlerCategoryPolicy.isBookTitle("示例书名", "示例")).isFalse();
        assertThat(CrawlerCategoryPolicy.sanitize("示例书名", "科幻")).isEqualTo("科幻");
        assertThat(CrawlerCategoryPolicy.sanitize("示例书名", "示例书名")).isNull();
        assertThat(CrawlerCategoryPolicy.isBookTitle(null, null)).isFalse();
    }

    private CrawlerCategoryCleanupCandidate candidate(long id, String title, String category, String libraryCategory) {
        var candidate = mock(CrawlerCategoryCleanupCandidate.class);
        when(candidate.getId()).thenReturn(id);
        when(candidate.getBookName()).thenReturn(title);
        when(candidate.getCategory()).thenReturn(category);
        when(candidate.getSiteName()).thenReturn("示例站");
        if (libraryCategory != null) {
            when(candidate.getLibraryBookId()).thenReturn(100L + id);
            when(candidate.getLibraryCategoryId()).thenReturn(200L + id);
            when(candidate.getLibraryCategoryName()).thenReturn(libraryCategory);
        }
        return candidate;
    }
}
