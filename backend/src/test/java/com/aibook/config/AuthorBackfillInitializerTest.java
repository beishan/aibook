package com.aibook.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.SystemConfigRepository;
import com.aibook.service.AuthorService;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class AuthorBackfillInitializerTest {

    @Test
    void backfillsCandidatesOnceAndStoresCompletionMarker() {
        BookRepository books = mock(BookRepository.class);
        SystemConfigRepository configs = mock(SystemConfigRepository.class);
        AuthorService authors = mock(AuthorService.class);
        EntityManager entityManager = mock(EntityManager.class);
        Book book = Book.builder()
                .id(8L)
                .title("旧书")
                .author("鲁迅")
                .user(User.builder().id(3L).username("reader").build())
                .build();
        when(configs.findById(AuthorBackfillInitializer.MIGRATION_KEY))
                .thenReturn(Optional.empty());
        when(books.findAuthorSynchronizationCandidatesAfterId(anyLong(), any(Pageable.class)))
                .thenReturn(List.of(book), List.of());

        new AuthorBackfillInitializer(books, configs, authors, entityManager).backfill();

        verify(authors).synchronizeBook(book);
        verify(books, times(2)).findAuthorSynchronizationCandidatesAfterId(
                anyLong(), any(Pageable.class));
        verify(entityManager).flush();
        verify(entityManager).clear();
        verify(configs).save(any());
    }
}
