package com.aibook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.Category;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.UserRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileScannerServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void scanAssignsDefaultCategoryOnlyWhenCreatingBook() throws Exception {
        Path bookFile = Files.writeString(tempDir.resolve("凡人修仙传.epub"), "epub-content");
        User user = User.builder().id(1L).username("reader").build();
        Category category = Category.builder()
                .id(2L)
                .name("修真")
                .user(user)
                .build();
        BookRepository bookRepository = mock(BookRepository.class);
        AtomicReference<Book> savedBook = new AtomicReference<>();
        when(bookRepository.findByFileHash(any())).thenReturn(Optional.empty());
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> {
            Book book = invocation.getArgument(0);
            savedBook.set(book);
            return book;
        });

        FileScannerService service = new FileScannerService(
                bookRepository,
                mock(UserRepository.class),
                mock(MetadataService.class),
                mock(TxtParserService.class));

        FileScannerService.ScanResult result =
                service.scanDirectory(tempDir.toString(), user, category);

        assertThat(result.getNewCount()).isEqualTo(1);
        assertThat(savedBook.get().getFilePath()).isEqualTo(bookFile.toString());
        assertThat(savedBook.get().getCategory()).isSameAs(category);
    }

    @Test
    void duplicateScanDoesNotOverwriteExistingCategory() throws Exception {
        Files.writeString(tempDir.resolve("三体.epub"), "same-content");
        User user = User.builder().id(1L).username("reader").build();
        Category existingCategory = Category.builder().id(2L).name("科幻").user(user).build();
        Category directoryDefault = Category.builder().id(3L).name("玄幻").user(user).build();
        Book existing = Book.builder()
                .id(10L)
                .title("三体")
                .format("epub")
                .filePath("/scanfolder/三体.epub")
                .category(existingCategory)
                .user(user)
                .build();
        BookRepository bookRepository = mock(BookRepository.class);
        when(bookRepository.findByFileHash(any())).thenReturn(Optional.of(existing));

        FileScannerService service = new FileScannerService(
                bookRepository,
                mock(UserRepository.class),
                mock(MetadataService.class),
                mock(TxtParserService.class));

        FileScannerService.ScanResult result =
                service.scanDirectory(tempDir.toString(), user, directoryDefault);

        assertThat(result.getSkippedCount()).isEqualTo(1);
        assertThat(existing.getCategory()).isSameAs(existingCategory);
    }
}
