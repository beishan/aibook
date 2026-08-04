package com.aibook.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookScanSource;
import com.aibook.model.entity.ScanDirectory;
import com.aibook.model.entity.SystemConfig;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.BookScanSourceBackfillProjection;
import com.aibook.repository.BookScanSourceRepository;
import com.aibook.repository.ScanDirectoryRepository;
import com.aibook.repository.SystemConfigRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

class BookScanSourceBackfillInitializerTest {

    @Test
    void backfillUsesPagedMinimalFieldsAndNormalizedPathComponents() {
        User user = User.builder().id(1L).build();
        ScanDirectory directory = ScanDirectory.builder()
                .id(2L).path("/books/fiction/../fiction").user(user).build();
        BookScanSourceBackfillProjection matching = projection(
                3L, 1L, "/books/fiction/novel.epub");
        BookScanSourceBackfillProjection prefixOnly = projection(
                4L, 1L, "/books/fictional/other.epub");
        ScanDirectoryRepository directoryRepository = mock(ScanDirectoryRepository.class);
        BookRepository bookRepository = mock(BookRepository.class);
        BookScanSourceRepository sourceRepository = mock(BookScanSourceRepository.class);
        SystemConfigRepository configRepository = mock(SystemConfigRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        when(directoryRepository.findAll()).thenReturn(List.of(directory));
        when(bookRepository.findBackfillCandidatesAfterId(anyLong(), any(Pageable.class)))
                .thenReturn(List.of(matching, prefixOnly), List.of());
        when(sourceRepository.findKeysByBookIds(List.of(3L, 4L))).thenReturn(List.of());
        when(entityManager.getReference(Book.class, 3L)).thenReturn(Book.builder().id(3L).build());
        when(entityManager.getReference(ScanDirectory.class, 2L)).thenReturn(directory);
        when(entityManager.getReference(User.class, 1L)).thenReturn(user);

        initializer(
                directoryRepository,
                bookRepository,
                sourceRepository,
                configRepository,
                entityManager).backfill();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BookScanSource>> sources = ArgumentCaptor.forClass(List.class);
        verify(sourceRepository).saveAll(sources.capture());
        assertThat(sources.getValue())
                .singleElement()
                .satisfies(source -> {
                    assertThat(source.getBook().getId()).isEqualTo(3L);
                    assertThat(source.getScanDirectory().getId()).isEqualTo(2L);
                });
        verify(bookRepository, never()).findAll();
        verify(entityManager).clear();
        verify(configRepository).save(any(SystemConfig.class));
    }

    @Test
    void completedMigrationDoesNotLoadBooksAgain() {
        ScanDirectoryRepository directoryRepository = mock(ScanDirectoryRepository.class);
        BookRepository bookRepository = mock(BookRepository.class);
        BookScanSourceRepository sourceRepository = mock(BookScanSourceRepository.class);
        SystemConfigRepository configRepository = mock(SystemConfigRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        when(configRepository.findById(BookScanSourceBackfillInitializer.MIGRATION_KEY))
                .thenReturn(Optional.of(SystemConfig.builder().configValue("complete").build()));

        initializer(
                directoryRepository,
                bookRepository,
                sourceRepository,
                configRepository,
                entityManager).backfill();

        verify(directoryRepository, never()).findAll();
        verify(bookRepository, never()).findBackfillCandidatesAfterId(
                anyLong(), any(Pageable.class));
    }

    private BookScanSourceBackfillInitializer initializer(
            ScanDirectoryRepository directoryRepository,
            BookRepository bookRepository,
            BookScanSourceRepository sourceRepository,
            SystemConfigRepository configRepository,
            EntityManager entityManager) {
        return new BookScanSourceBackfillInitializer(
                directoryRepository,
                bookRepository,
                sourceRepository,
                configRepository,
                entityManager);
    }

    private BookScanSourceBackfillProjection projection(Long id, Long userId, String filePath) {
        BookScanSourceBackfillProjection projection = mock(BookScanSourceBackfillProjection.class);
        when(projection.getId()).thenReturn(id);
        when(projection.getUserId()).thenReturn(userId);
        when(projection.getFilePath()).thenReturn(filePath);
        return projection;
    }
}
