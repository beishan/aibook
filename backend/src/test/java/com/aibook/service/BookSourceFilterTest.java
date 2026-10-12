package com.aibook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.User;
import com.aibook.repository.*;
import jakarta.persistence.criteria.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

class BookSourceFilterTest {
    private final BookRepository repository = mock(BookRepository.class);
    private final CategoryService categories = mock(CategoryService.class);
    private final BookService service = new BookService(repository,
            mock(ReadingProgressRepository.class), mock(BookmarkRepository.class),
            mock(BookHighlightRepository.class), mock(BookListRepository.class), categories,
            mock(TagService.class), mock(AuthorService.class), mock(OperationLogService.class));
    private final User user = User.builder().id(1L).build();

    @Test
    @SuppressWarnings("unchecked")
    void combinesSourceWebsiteWithAccountDeletionKeywordAndExistingFilters() {
        Pageable pageable = PageRequest.of(2, 20);
        when(categories.getCategoryAndDescendantIds(7L, user)).thenReturn(List.of(7L, 8L));
        when(repository.findAll(any(Specification.class), eq(pageable))).thenReturn(Page.empty(pageable));
        service.filterBooks(user, " 三体 ", "epub", "READING", 7L, true, 9L,
                "CRAWLER", "示例网站", pageable);
        ArgumentCaptor<Specification<Book>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repository).findAll(captor.capture(), eq(pageable));
        Root<Book> root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaQuery<?> query = mock(CriteriaQuery.class, RETURNS_DEEP_STUBS);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        captor.getValue().toPredicate(root, query, cb);
        verify(cb).equal(root.get("user"), user);
        verify(cb).isNull(root.get("deletedAt"));
        verify(cb, times(2)).exists(any(Subquery.class));
        verify(cb).not(any());
        verify(cb).equal(root.get("sourceType"), Book.SourceType.CRAWLER);
        verify(cb).equal(root.get("sourceSiteName"), "示例网站");
        verify(cb).equal(root.get("format"), "epub");
        verify(cb).equal(root.get("readingStatus"), Book.ReadingStatus.READING);
        verify(root.get("category").get("id")).in(List.of(7L, 8L));
        verify(cb).equal(root.join("tags").get("id"), 9L);
        verify(query).distinct(true);
        verify(cb, times(4)).like(any(), eq("%三体%"));
        verify(categories).getOwnedCategory(7L, user);
    }

    @Test
    @SuppressWarnings("unchecked")
    void supportsUploadScanAndAllCrawlerSourcesWithoutWebsite() {
        for (Book.SourceType source : Book.SourceType.values()) {
            when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
            service.filterBooks(user, null, null, null, null, false, null,
                    source.name(), null, PageRequest.of(0, 20));
        }
        verify(repository, times(3)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void rejectsInvalidSourceAndWebsiteOnNonCrawlerSource() {
        assertThatThrownBy(() -> service.filterBooks(user, null, null, null, null, false,
                null, "INVALID", null, PageRequest.of(0, 20)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.filterBooks(user, null, null, null, null, false,
                null, "UPLOAD", "示例网站", PageRequest.of(0, 20)))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void sourceOptionsAreScopedToCurrentAccountAndCrawlerType() {
        when(repository.findSourceSites(user, Book.SourceType.CRAWLER)).thenReturn(List.of("示例网站"));
        assertThat(service.sourceSites(user)).containsExactly("示例网站");
        verify(repository).findSourceSites(user, Book.SourceType.CRAWLER);
    }
}
