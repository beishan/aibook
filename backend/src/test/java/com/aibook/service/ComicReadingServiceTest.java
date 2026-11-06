package com.aibook.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookVersion;
import com.aibook.model.entity.User;
import com.aibook.model.entity.VersionReadingProgress;
import com.aibook.repository.BookRepository;
import com.aibook.repository.VersionReadingProgressRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ComicReadingServiceTest {

    private final BookRepository books = mock(BookRepository.class);
    private final BookVersionService versions = mock(BookVersionService.class);
    private final VersionReadingProgressRepository positions = mock(VersionReadingProgressRepository.class);
    private final ReadingProgressService progress = mock(ReadingProgressService.class);
    private final ComicArchiveService archives = mock(ComicArchiveService.class);
    private final User user = User.builder().id(1L).build();
    private final Book book = Book.builder().id(2L).user(user).build();
    private final BookVersion version = BookVersion.builder().id(3L)
            .book(book).format("cbz").filePath("comic.cbz").fileHash("hash").build();
    private final ComicArchiveService.Source source =
            new ComicArchiveService.Source(Path.of("comic.cbz"), 100, "current");
    private final ComicReadingService service = new ComicReadingService(
            books, versions, positions, progress, archives, mock(BookParsingService.class), new ObjectMapper());

    @BeforeEach
    void bindOwnedVersion() {
        when(books.findOwnedBookForUpdate(2L, user)).thenReturn(Optional.of(book));
        when(versions.resolveVersion(book, 3L)).thenReturn(version);
    }

    @Test
    void rejectsOldWriteAndPreservesCompletionWhenReadingEarlierPages() {
        var archive = new ComicArchiveService.Archive(source, List.of(
                new ComicArchiveService.Entry("1.png", 0), new ComicArchiveService.Entry("2.png", 1),
                new ComicArchiveService.Entry("3.png", 2), new ComicArchiveService.Entry("4.png", 3)));
        when(archives.source("comic.cbz", "hash")).thenReturn(source);
        when(archives.archive(source)).thenReturn(archive);
        var stored = VersionReadingProgress.builder().version(version).user(user).build();
        when(positions.findByUserAndVersionForUpdate(user, version)).thenReturn(Optional.of(stored));
        when(progress.savePosition(eq(book), eq(user), eq(stored), anyString(), anyString(),
                anyInt(), anyInt(), any())).thenAnswer(invocation -> {
                    stored.setLocator(invocation.getArgument(7));
                    return null;
                });

        var finished = service.save(2L, 3L, user,
                new ComicReadingService.ProgressUpdate(4, 0, true, source.revision(), null));
        assertTrue(finished.finished());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () ->
                service.save(2L, 3L, user,
                        new ComicReadingService.ProgressUpdate(1, 0, false, source.revision(), null)))
                .getStatusCode());
        var reread = service.save(2L, 3L, user,
                new ComicReadingService.ProgressUpdate(2, 0.5, false, source.revision(), finished.token()));
        assertEquals(2, reread.page());
        assertTrue(reread.finished());
        assertNotEquals(finished.token(), reread.token());
    }

    @Test
    void rejectsPositionFromReplacedArchiveBeforeWriting() {
        when(archives.source("comic.cbz", "hash")).thenReturn(source);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () ->
                service.save(2L, 3L, user,
                        new ComicReadingService.ProgressUpdate(1, 0, false, "old-file", null)))
                .getStatusCode());
        verifyNoInteractions(positions, progress);
    }

    @Test
    void checksBookOwnershipBeforeReadingArchive() {
        User other = User.builder().id(99L).build();
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () ->
                service.page(2L, 3L, other, source.revision(), 1)).getStatusCode());
        verifyNoInteractions(versions, archives);
    }
}
