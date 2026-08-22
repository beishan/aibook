package com.aibook.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.SystemConfig;
import com.aibook.repository.BookRepository;
import com.aibook.repository.BookSourceTypeBackfillProjection;
import com.aibook.repository.SystemConfigRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class BookSourceTypeBackfillInitializerTest {

    @Test
    void backfillClassifiesOnlyNormalizedUploadDirectoryPathsAsUploads() {
        BookSourceTypeBackfillProjection uploaded = projection(3L, "/data/uploads/../uploads/book.epub");
        BookSourceTypeBackfillProjection scanned = projection(4L, "/books/fiction/book.epub");
        BookRepository bookRepository = mock(BookRepository.class);
        SystemConfigRepository configRepository = mock(SystemConfigRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        Book uploadedBook = Book.builder().id(3L).build();
        Book scannedBook = Book.builder().id(4L).build();
        when(bookRepository.findSourceTypeBackfillCandidatesAfterId(anyLong(), any(Pageable.class)))
                .thenReturn(List.of(uploaded, scanned), List.of());
        when(entityManager.getReference(Book.class, 3L)).thenReturn(uploadedBook);
        when(entityManager.getReference(Book.class, 4L)).thenReturn(scannedBook);

        initializer(bookRepository, configRepository, entityManager, "/data/uploads").backfill();

        assertThat(uploadedBook.getSourceType()).isEqualTo(Book.SourceType.UPLOAD);
        assertThat(scannedBook.getSourceType()).isEqualTo(Book.SourceType.DIRECTORY_SCAN);
        verify(bookRepository, never()).findAll();
        verify(entityManager).flush();
        verify(entityManager).clear();
        verify(configRepository).save(any(SystemConfig.class));
    }

    @Test
    void completedMigrationDoesNotReadBooksAgain() {
        BookRepository bookRepository = mock(BookRepository.class);
        SystemConfigRepository configRepository = mock(SystemConfigRepository.class);
        when(configRepository.findById(BookSourceTypeBackfillInitializer.MIGRATION_KEY))
                .thenReturn(Optional.of(SystemConfig.builder().configValue("complete").build()));

        initializer(bookRepository, configRepository, mock(EntityManager.class), "/data/uploads").backfill();

        verify(bookRepository, never()).findSourceTypeBackfillCandidatesAfterId(
                anyLong(), any(Pageable.class));
    }

    private BookSourceTypeBackfillInitializer initializer(
            BookRepository bookRepository,
            SystemConfigRepository configRepository,
            EntityManager entityManager,
            String uploadPath) {
        return new BookSourceTypeBackfillInitializer(
                bookRepository, configRepository, entityManager, uploadPath);
    }

    private BookSourceTypeBackfillProjection projection(Long id, String filePath) {
        BookSourceTypeBackfillProjection projection = mock(BookSourceTypeBackfillProjection.class);
        when(projection.getId()).thenReturn(id);
        when(projection.getFilePath()).thenReturn(filePath);
        return projection;
    }
}
