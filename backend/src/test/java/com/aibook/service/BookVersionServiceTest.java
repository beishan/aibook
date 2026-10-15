package com.aibook.service;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookVersion;
import com.aibook.repository.BookRepository;
import com.aibook.repository.BookVersionRepository;
import com.aibook.repository.ReadingProgressRepository;
import com.aibook.repository.ReadingDailyActivityRepository;
import com.aibook.repository.VersionReadingProgressRepository;
import com.aibook.repository.LibraryChapterRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookVersionServiceTest {

    @Test
    void persistsSelectionWithoutChangingEitherVersionsProgress() {
        BookVersionRepository versions = mock(BookVersionRepository.class);
        BookRepository books = mock(BookRepository.class);
        VersionReadingProgressRepository progress = mock(VersionReadingProgressRepository.class);
        BookVersionService service = new BookVersionService(
                versions, books, mock(ReadingProgressRepository.class), progress,
                mock(ReadingDailyActivityRepository.class), mock(LibraryChapterRepository.class),
                new TxtParserService(), new ObjectMapper());
        Book book = Book.builder().id(1L).selectedVersionId(10L).build();
        BookVersion revised = BookVersion.builder().id(11L).book(book)
                .displayName("修订版").format("txt").build();
        when(versions.findByIdAndBook(11L, book)).thenReturn(Optional.of(revised));

        var selected = service.selectVersion(book, 11L);

        assertEquals(11L, book.getSelectedVersionId());
        org.junit.jupiter.api.Assertions.assertTrue(selected.isSelected());
        org.mockito.Mockito.verify(books).save(book);
        org.mockito.Mockito.verifyNoInteractions(progress);
        when(versions.findByIdAndBook(99L, book)).thenReturn(Optional.empty());
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.selectVersion(book, 99L));
        assertEquals(11L, book.getSelectedVersionId());
    }

    @Test
    void shouldUseOriginalUploadFilenameForPrimaryVersion() {
        BookVersionRepository versionRepository = mock(BookVersionRepository.class);
        ReadingProgressRepository progressRepository = mock(ReadingProgressRepository.class);
        when(versionRepository.findByBookAndPrimaryVersionTrue(any(Book.class)))
                .thenReturn(Optional.empty());
        when(versionRepository.save(any(BookVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(progressRepository.findAllByBook(any(Book.class))).thenReturn(List.of());

        BookVersionService service = new BookVersionService(
                versionRepository,
                mock(BookRepository.class),
                progressRepository,
                mock(VersionReadingProgressRepository.class),
                mock(ReadingDailyActivityRepository.class),
                mock(LibraryChapterRepository.class),
                new TxtParserService(),
                new ObjectMapper());
        Book book = Book.builder()
                .title("解析后的书名")
                .format("txt")
                .filePath("uploads/cfe3012c-dff6-4b6e-b3bc-4af90d3fcae3.txt")
                .build();

        BookVersion version = service.ensurePrimaryVersion(
                book, "原始上传书名.txt");

        assertEquals("原始上传书名.txt", version.getDisplayName());
    }
}
