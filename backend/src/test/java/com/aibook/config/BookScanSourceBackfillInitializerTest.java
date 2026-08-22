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
import com.aibook.repository.BookVersionRepository;
import com.aibook.repository.BookVersionScanSourceBackfillProjection;
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
        BookVersionRepository versionRepository = mock(BookVersionRepository.class);
        SystemConfigRepository configRepository = mock(SystemConfigRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        when(directoryRepository.findAll()).thenReturn(List.of(directory));
        when(bookRepository.findBackfillCandidatesAfterId(anyLong(), any(Pageable.class)))
                .thenReturn(List.of(matching, prefixOnly), List.of());
        when(sourceRepository.findKeysByBookIds(List.of(3L, 4L))).thenReturn(List.of());
        when(versionRepository.findScanSourceBackfillCandidatesAfterId(anyLong(), any(Pageable.class)))
                .thenReturn(List.of());
        when(entityManager.getReference(Book.class, 3L)).thenReturn(Book.builder().id(3L).build());
        when(entityManager.getReference(ScanDirectory.class, 2L)).thenReturn(directory);
        when(entityManager.getReference(User.class, 1L)).thenReturn(user);

        initializer(
                directoryRepository,
                bookRepository,
                versionRepository,
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
        BookVersionRepository versionRepository = mock(BookVersionRepository.class);
        BookScanSourceRepository sourceRepository = mock(BookScanSourceRepository.class);
        SystemConfigRepository configRepository = mock(SystemConfigRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        when(configRepository.findById(BookScanSourceBackfillInitializer.MIGRATION_KEY))
                .thenReturn(Optional.of(SystemConfig.builder().configValue("complete").build()));

        initializer(
                directoryRepository,
                bookRepository,
                versionRepository,
                sourceRepository,
                configRepository,
                entityManager).backfill();

        verify(directoryRepository, never()).findAll();
        verify(bookRepository, never()).findBackfillCandidatesAfterId(
                anyLong(), any(Pageable.class));
        verify(versionRepository, never()).findScanSourceBackfillCandidatesAfterId(
                anyLong(), any(Pageable.class));
    }

    @Test
    void v1CompletionDoesNotSkipV2VersionPathBackfillOrNestedDirectories() {
        User user = User.builder().id(1L).build();
        User otherUser = User.builder().id(9L).build();
        ScanDirectory directory = ScanDirectory.builder()
                .id(2L).path("/books").user(user).build();
        ScanDirectory nestedDirectory = ScanDirectory.builder()
                .id(4L).path("/books/alternate").user(user).build();
        ScanDirectory foreignDirectory = ScanDirectory.builder()
                .id(5L).path("/books").user(otherUser).build();
        BookVersionScanSourceBackfillProjection version = versionProjection(
                5L, 3L, 1L, "/books/alternate/novel.pdf");
        ScanDirectoryRepository directoryRepository = mock(ScanDirectoryRepository.class);
        BookRepository bookRepository = mock(BookRepository.class);
        BookVersionRepository versionRepository = mock(BookVersionRepository.class);
        BookScanSourceRepository sourceRepository = mock(BookScanSourceRepository.class);
        SystemConfigRepository configRepository = mock(SystemConfigRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        when(configRepository.findById("migration.book-scan-sources.v1"))
                .thenReturn(Optional.of(SystemConfig.builder().configValue("complete").build()));
        when(directoryRepository.findAll()).thenReturn(
                List.of(directory, nestedDirectory, foreignDirectory));
        when(bookRepository.findBackfillCandidatesAfterId(anyLong(), any(Pageable.class)))
                .thenReturn(List.of());
        when(versionRepository.findScanSourceBackfillCandidatesAfterId(anyLong(), any(Pageable.class)))
                .thenReturn(List.of(version), List.of());
        when(sourceRepository.findKeysByBookIds(List.of(3L))).thenReturn(List.of());
        when(entityManager.getReference(Book.class, 3L)).thenReturn(Book.builder().id(3L).build());
        when(entityManager.getReference(ScanDirectory.class, 2L)).thenReturn(directory);
        when(entityManager.getReference(ScanDirectory.class, 4L)).thenReturn(nestedDirectory);
        when(entityManager.getReference(User.class, 1L)).thenReturn(user);

        initializer(
                directoryRepository,
                bookRepository,
                versionRepository,
                sourceRepository,
                configRepository,
                entityManager).backfill();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BookScanSource>> sources = ArgumentCaptor.forClass(List.class);
        verify(sourceRepository).saveAll(sources.capture());
        assertThat(sources.getValue())
                .hasSize(2)
                .allSatisfy(source -> assertThat(source.getBook().getId()).isEqualTo(3L));
        assertThat(sources.getValue())
                .extracting(source -> source.getScanDirectory().getId())
                .containsExactlyInAnyOrder(2L, 4L);
        verify(configRepository).findById(BookScanSourceBackfillInitializer.MIGRATION_KEY);
    }

    private BookScanSourceBackfillInitializer initializer(
            ScanDirectoryRepository directoryRepository,
            BookRepository bookRepository,
            BookVersionRepository versionRepository,
            BookScanSourceRepository sourceRepository,
            SystemConfigRepository configRepository,
            EntityManager entityManager) {
        return new BookScanSourceBackfillInitializer(
                directoryRepository,
                bookRepository,
                versionRepository,
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

    private BookVersionScanSourceBackfillProjection versionProjection(
            Long id, Long bookId, Long userId, String filePath) {
        BookVersionScanSourceBackfillProjection projection =
                mock(BookVersionScanSourceBackfillProjection.class);
        when(projection.getId()).thenReturn(id);
        when(projection.getBookId()).thenReturn(bookId);
        when(projection.getUserId()).thenReturn(userId);
        when(projection.getFilePath()).thenReturn(filePath);
        return projection;
    }
}
