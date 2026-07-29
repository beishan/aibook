package com.aibook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.Category;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
        ScannedBookPersistenceService persistenceService =
                mock(ScannedBookPersistenceService.class);
        AtomicReference<Book> savedBook = new AtomicReference<>();
        AtomicReference<Long> savedUserId = new AtomicReference<>();
        AtomicReference<Long> savedCategoryId = new AtomicReference<>();
        when(bookRepository.findByFileHash(any())).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            Book book = invocation.getArgument(0);
            savedBook.set(book);
            savedUserId.set(invocation.getArgument(1));
            savedCategoryId.set(invocation.getArgument(2));
            return book;
        }).when(persistenceService).save(any(Book.class), any(), any());

        FileScannerService service = new FileScannerService(
                bookRepository,
                persistenceService,
                mock(MetadataService.class),
                mock(TxtParserService.class),
                mock(BookParsingService.class));

        FileScannerService.ScanResult result =
                service.scanDirectory(tempDir.toString(), user, category.getId());

        assertThat(result.getNewCount()).isEqualTo(1);
        assertThat(result.getTotalCount()).isEqualTo(1);
        assertThat(result.getScannedCount()).isEqualTo(1);
        assertThat(result.getProgressPercent()).isEqualTo(100);
        assertThat(savedBook.get().getFilePath()).isEqualTo(bookFile.toString());
        assertThat(savedBook.get().getUser()).isNull();
        assertThat(savedBook.get().getCategory()).isNull();
        assertThat(savedUserId.get()).isEqualTo(user.getId());
        assertThat(savedCategoryId.get()).isEqualTo(category.getId());
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
                mock(ScannedBookPersistenceService.class),
                mock(MetadataService.class),
                mock(TxtParserService.class),
                mock(BookParsingService.class));

        FileScannerService.ScanResult result =
                service.scanDirectory(
                        tempDir.toString(),
                        user,
                        directoryDefault.getId());

        assertThat(result.getSkippedCount()).isEqualTo(1);
        assertThat(existing.getCategory()).isSameAs(existingCategory);
    }

    @Test
    void scanProcessesFilesWithConfiguredConcurrency() throws Exception {
        for (int index = 0; index < 4; index++) {
            Files.writeString(
                    tempDir.resolve("book-" + index + ".epub"),
                    "epub-content-" + index);
        }
        User user = User.builder()
                .id(1L)
                .username("reader")
                .scanThreadCount(3)
                .build();
        BookRepository bookRepository = mock(BookRepository.class);
        ScannedBookPersistenceService persistenceService =
                mock(ScannedBookPersistenceService.class);
        AtomicInteger activeWorkers = new AtomicInteger();
        AtomicInteger maxActiveWorkers = new AtomicInteger();
        CountDownLatch concurrentWorkers = new CountDownLatch(2);
        when(bookRepository.findByFileHash(any())).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            int active = activeWorkers.incrementAndGet();
            maxActiveWorkers.accumulateAndGet(active, Math::max);
            concurrentWorkers.countDown();
            concurrentWorkers.await(2, TimeUnit.SECONDS);
            activeWorkers.decrementAndGet();
            return invocation.getArgument(0);
        }).when(persistenceService).save(any(Book.class), any(), any());

        FileScannerService service = new FileScannerService(
                bookRepository,
                persistenceService,
                mock(MetadataService.class),
                mock(TxtParserService.class),
                mock(BookParsingService.class));

        FileScannerService.ScanResult result =
                service.scanDirectory(tempDir.toString(), user);

        assertThat(result.getThreadCount()).isEqualTo(3);
        assertThat(result.getNewCount()).isEqualTo(4);
        assertThat(result.getTotalCount()).isEqualTo(4);
        assertThat(result.getScannedCount()).isEqualTo(4);
        assertThat(maxActiveWorkers.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void concurrentScanImportsDuplicateContentOnlyOnce() throws Exception {
        Files.writeString(tempDir.resolve("副本一.epub"), "same-content");
        Files.writeString(tempDir.resolve("副本二.epub"), "same-content");
        User user = User.builder()
                .id(1L)
                .username("reader")
                .scanThreadCount(2)
                .build();
        BookRepository bookRepository = mock(BookRepository.class);
        ScannedBookPersistenceService persistenceService =
                mock(ScannedBookPersistenceService.class);
        when(bookRepository.findByFileHash(any())).thenReturn(Optional.empty());

        FileScannerService service = new FileScannerService(
                bookRepository,
                persistenceService,
                mock(MetadataService.class),
                mock(TxtParserService.class),
                mock(BookParsingService.class));

        FileScannerService.ScanResult result =
                service.scanDirectory(tempDir.toString(), user);

        assertThat(result.getNewCount()).isEqualTo(1);
        assertThat(result.getSkippedCount()).isEqualTo(1);
        assertThat(result.getTotalCount()).isEqualTo(2);
        assertThat(result.getScannedCount()).isEqualTo(2);
        verify(persistenceService, times(1))
                .save(any(Book.class), eq(user.getId()), eq(null));
    }
}
