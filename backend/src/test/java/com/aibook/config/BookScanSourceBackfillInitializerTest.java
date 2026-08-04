package com.aibook.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.ScanDirectory;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.BookScanSourceRepository;
import com.aibook.repository.ScanDirectoryRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class BookScanSourceBackfillInitializerTest {

    @Test
    void backfillUsesNormalizedPathComponentsInsteadOfStringPrefix() {
        User user = User.builder().id(1L).build();
        ScanDirectory directory = ScanDirectory.builder()
                .id(2L).path("/books/fiction/../fiction").user(user).build();
        Book matching = Book.builder()
                .id(3L).filePath("/books/fiction/novel.epub").user(user).build();
        Book prefixOnly = Book.builder()
                .id(4L).filePath("/books/fictional/other.epub").user(user).build();
        ScanDirectoryRepository directoryRepository = mock(ScanDirectoryRepository.class);
        BookRepository bookRepository = mock(BookRepository.class);
        BookScanSourceRepository sourceRepository = mock(BookScanSourceRepository.class);
        when(directoryRepository.findAll()).thenReturn(List.of(directory));
        when(bookRepository.findAll()).thenReturn(List.of(matching, prefixOnly));
        when(sourceRepository.existsByBookAndScanDirectory(any(Book.class), any(ScanDirectory.class)))
                .thenReturn(false);

        new BookScanSourceBackfillInitializer(
                directoryRepository, bookRepository, sourceRepository).backfill();

        verify(sourceRepository).save(any());
        verify(sourceRepository, never()).existsByBookAndScanDirectory(prefixOnly, directory);
    }
}
