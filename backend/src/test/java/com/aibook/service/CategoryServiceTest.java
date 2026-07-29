package com.aibook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibook.dto.CategoryDTO;
import com.aibook.dto.CategoryMoveRequest;
import com.aibook.dto.CategoryRequest;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.Category;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.CategoryRepository;
import com.aibook.repository.ScanDirectoryRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CategoryServiceTest {

    @Test
    void initializePresetsCreatesCommonNovelCategoriesIdempotently() {
        User user = user();
        CategoryRepository categoryRepository = mock(CategoryRepository.class);
        BookRepository bookRepository = mock(BookRepository.class);
        List<Category> stored = new ArrayList<>();
        AtomicLong sequence = new AtomicLong(1);

        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category category = invocation.getArgument(0);
            if (category.getId() == null) {
                category.setId(sequence.getAndIncrement());
                stored.add(category);
            }
            return category;
        });
        when(categoryRepository.countByUser(user)).thenAnswer(invocation -> (long) stored.size());
        when(categoryRepository.findByUserOrderBySortOrderAscNameAsc(user))
                .thenAnswer(invocation -> sorted(stored));
        when(categoryRepository.findByUserAndParentOrderBySortOrderAscNameAsc(
                any(User.class), nullable(Category.class)))
                .thenAnswer(invocation -> {
                    Category parent = invocation.getArgument(1);
                    return sorted(stored.stream()
                            .filter(category -> category.getParent() == parent)
                            .toList());
                });
        when(bookRepository.countByUserAndCategoryAndDeletedAtIsNull(
                any(User.class), any(Category.class)))
                .thenReturn(0L);

        CategoryService service = new CategoryService(
                categoryRepository, bookRepository, mock(ScanDirectoryRepository.class));
        List<CategoryDTO> first = service.initializePresets(user);
        int firstCount = stored.size();
        service.initializePresets(user);

        CategoryDTO onlineLiterature = first.stream()
                .filter(category -> category.getName().equals("网络文学"))
                .findFirst()
                .orElseThrow();

        assertThat(onlineLiterature.getChildren())
                .extracting(CategoryDTO::getName)
                .contains("玄幻", "修真", "仙侠");
        assertThat(stored).hasSize(firstCount);
    }

    @Test
    void moveRejectsMovingCategoryBelowItsChild() {
        User user = user();
        Category parent = category(1L, "文学", user, null);
        Category child = category(2L, "小说", user, parent);
        CategoryRepository categoryRepository = mock(CategoryRepository.class);
        when(categoryRepository.findByIdAndUser(1L, user)).thenReturn(Optional.of(parent));
        when(categoryRepository.findByIdAndUser(2L, user)).thenReturn(Optional.of(child));

        CategoryService service =
                new CategoryService(
                        categoryRepository,
                        mock(BookRepository.class),
                        mock(ScanDirectoryRepository.class));
        CategoryMoveRequest request = new CategoryMoveRequest();
        request.setParentId(2L);

        assertThatThrownBy(() -> service.moveCategory(1L, request, user))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("子分类");
    }

    @Test
    void deleteCategoryUnassignsBooksButDoesNotDeleteThem() {
        User user = user();
        Category category = category(1L, "玄幻", user, null);
        Book book = Book.builder()
                .id(10L)
                .title("测试书")
                .format("epub")
                .filePath("/scanfolder/test.epub")
                .category(category)
                .user(user)
                .build();
        CategoryRepository categoryRepository = mock(CategoryRepository.class);
        BookRepository bookRepository = mock(BookRepository.class);
        when(categoryRepository.findByIdAndUser(1L, user)).thenReturn(Optional.of(category));
        when(categoryRepository.findByUserAndParentOrderBySortOrderAscNameAsc(user, category))
                .thenReturn(List.of());
        when(bookRepository.findByUserAndCategoryAndDeletedAtIsNull(user, category))
                .thenReturn(List.of(book));

        CategoryService service = new CategoryService(
                categoryRepository, bookRepository, mock(ScanDirectoryRepository.class));
        service.deleteCategory(1L, null, user);

        assertThat(book.getCategory()).isNull();
        verify(bookRepository).saveAll(List.of(book));
        verify(categoryRepository).delete(category);
    }

    @Test
    void createCategoryRejectsDuplicateSiblingName() {
        User user = user();
        CategoryRepository categoryRepository = mock(CategoryRepository.class);
        when(categoryRepository.existsByUserAndParentAndNameIgnoreCase(user, null, "玄幻"))
                .thenReturn(true);
        CategoryRequest request = new CategoryRequest();
        request.setName(" 玄幻 ");

        CategoryService service =
                new CategoryService(
                        categoryRepository,
                        mock(BookRepository.class),
                        mock(ScanDirectoryRepository.class));

        assertThatThrownBy(() -> service.createCategory(user, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("同级分类名称已存在");
    }

    private static List<Category> sorted(List<Category> categories) {
        return categories.stream()
                .sorted(Comparator.comparing(Category::getSortOrder)
                        .thenComparing(Category::getName))
                .toList();
    }

    private static User user() {
        return User.builder().id(1L).username("reader").build();
    }

    private static Category category(Long id, String name, User user, Category parent) {
        return Category.builder()
                .id(id)
                .name(name)
                .user(user)
                .parent(parent)
                .sortOrder(0)
                .enabled(true)
                .builtIn(false)
                .build();
    }
}
