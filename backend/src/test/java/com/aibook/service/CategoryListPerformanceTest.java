package com.aibook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.aibook.model.entity.Category;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.CategoryRepository;
import com.aibook.repository.ScanDirectoryRepository;
import com.aibook.repository.projections.BookStatisticsProjections.BookCategoryCount;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CategoryListPerformanceTest {
    @Test
    void largeTreeUsesOneGroupedCountAndAggregatesThreeLevels() {
        User user = User.builder().id(1L).build();
        Category root = Category.builder().id(1L).name("根").user(user).build();
        Category child = Category.builder().id(2L).name("子").user(user).parent(root).build();
        Category leaf = Category.builder().id(3L).name("末级").user(user).parent(child).build();
        List<Category> categories = new ArrayList<>(List.of(root, child, leaf));
        for (long id = 4; id <= 500; id++) {
            categories.add(Category.builder().id(id).name("分类" + id).user(user).build());
        }
        CategoryRepository repository = mock(CategoryRepository.class);
        BookRepository books = mock(BookRepository.class);
        when(repository.countByUser(user)).thenReturn(500L);
        when(repository.findByUserOrderBySortOrderAscNameAsc(user)).thenReturn(categories);
        List<BookCategoryCount> counts = List.of(count(1L, 2L), count(2L, 3L), count(3L, 5L));
        when(books.countByCategory(user)).thenReturn(counts);
        CategoryService service = new CategoryService(repository, books, mock(ScanDirectoryRepository.class));
        var result = service.getCategoryTree(user);
        assertThat(result).hasSize(498);
        assertThat(result.getFirst().getDirectBookCount()).isEqualTo(2L);
        assertThat(result.getFirst().getBookCount()).isEqualTo(10L);
        assertThat(result.getFirst().getChildren().getFirst().getBookCount()).isEqualTo(8L);
        assertThat(result.get(1).getBookCount()).isZero();
        verify(books).countByCategory(user);
        verify(books, never()).countByUserAndCategoryAndDeletedAtIsNull(any(), any());
        verifyNoMoreInteractions(books);
    }

    private BookCategoryCount count(long id, long total) {
        BookCategoryCount row = mock(BookCategoryCount.class);
        when(row.getCategoryId()).thenReturn(id);
        when(row.getCnt()).thenReturn(total);
        return row;
    }
}
