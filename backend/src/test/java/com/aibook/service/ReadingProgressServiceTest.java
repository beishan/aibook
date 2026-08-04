package com.aibook.service;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookVersion;
import com.aibook.model.entity.User;
import com.aibook.model.entity.VersionReadingProgress;
import com.aibook.repository.BookRepository;
import com.aibook.repository.ReadingProgressRepository;
import com.aibook.repository.VersionReadingProgressRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadingProgressServiceTest {

    @Test
    void savesIndependentProgressForEachBookVersion() {
        User user = User.builder().id(1L).username("reader").build();
        Book book = Book.builder()
                .id(9L)
                .title("多版本书籍")
                .format("epub")
                .filePath("/books/original.epub")
                .user(user)
                .build();
        BookVersion epubVersion = BookVersion.builder()
                .id(11L)
                .book(book)
                .displayName("原版.epub")
                .format("epub")
                .filePath("/books/original.epub")
                .primaryVersion(true)
                .build();
        BookVersion textVersion = BookVersion.builder()
                .id(12L)
                .book(book)
                .displayName("修订版.txt")
                .format("txt")
                .filePath("/books/revised.txt")
                .build();

        ReadingProgressRepository aggregateRepository =
                mock(ReadingProgressRepository.class);
        VersionReadingProgressRepository versionRepository =
                mock(VersionReadingProgressRepository.class);
        BookRepository bookRepository = mock(BookRepository.class);
        BookVersionService versionService = mock(BookVersionService.class);
        ReadingProgressService service = new ReadingProgressService(
                aggregateRepository,
                versionRepository,
                bookRepository,
                versionService);

        when(bookRepository.findByIdAndUserAndDeletedAtIsNull(9L, user))
                .thenReturn(Optional.of(book));
        when(versionService.resolveVersion(book, 11L)).thenReturn(epubVersion);
        when(versionService.resolveVersion(book, 12L)).thenReturn(textVersion);
        when(versionRepository.findByUserAndVersion(user, epubVersion))
                .thenReturn(Optional.empty());
        when(versionRepository.findByUserAndVersion(user, textVersion))
                .thenReturn(Optional.empty());
        when(versionRepository.save(any(VersionReadingProgress.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(aggregateRepository.findByUserAndBook(user, book))
                .thenReturn(Optional.empty());

        com.aibook.dto.ReadingProgressDTO epubProgress = service.saveProgress(
                9L, 11L, user, "epubcfi(/6/2)", "第一章", 0, 25);
        com.aibook.dto.ReadingProgressDTO textProgress = service.saveProgress(
                9L, 12L, user, "第十章", "第十章", 0, 60);

        assertThat(epubProgress.getVersionId()).isEqualTo(11L);
        assertThat(epubProgress.getTotalProgress()).isEqualTo(25);
        assertThat(textProgress.getVersionId()).isEqualTo(12L);
        assertThat(textProgress.getTotalProgress()).isEqualTo(60);
        assertThat(epubProgress.getCurrentChapter())
                .isNotEqualTo(textProgress.getCurrentChapter());
    }

    @Test
    void marksBookAsReadingWhenInitialProgressIsStillZero() {
        User user = User.builder().id(1L).username("reader").build();
        Book book = Book.builder()
                .id(9L)
                .title("刚打开的书")
                .format("epub")
                .filePath("/books/new.epub")
                .user(user)
                .readingStatus(Book.ReadingStatus.UNREADING)
                .build();
        BookVersion version = BookVersion.builder()
                .id(11L)
                .book(book)
                .format("epub")
                .filePath("/books/new.epub")
                .primaryVersion(true)
                .build();

        ReadingProgressRepository aggregateRepository = mock(ReadingProgressRepository.class);
        VersionReadingProgressRepository versionRepository =
                mock(VersionReadingProgressRepository.class);
        BookRepository bookRepository = mock(BookRepository.class);
        BookVersionService versionService = mock(BookVersionService.class);
        ReadingProgressService service = new ReadingProgressService(
                aggregateRepository, versionRepository, bookRepository, versionService);

        when(bookRepository.findByIdAndUserAndDeletedAtIsNull(9L, user))
                .thenReturn(Optional.of(book));
        when(versionService.resolveVersion(book, 11L)).thenReturn(version);
        when(versionRepository.findByUserAndVersion(user, version)).thenReturn(Optional.empty());
        when(versionRepository.save(any(VersionReadingProgress.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(aggregateRepository.findByUserAndBook(user, book)).thenReturn(Optional.empty());

        service.saveProgress(9L, 11L, user, "epubcfi(/6/2)", "第一页", 0, 0);

        assertThat(book.getReadingStatus()).isEqualTo(Book.ReadingStatus.READING);
        verify(bookRepository).save(book);
    }
}
