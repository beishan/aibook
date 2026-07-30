package com.aibook.service;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookVersion;
import com.aibook.model.entity.User;
import com.aibook.repository.BookHighlightRepository;
import com.aibook.repository.BookListRepository;
import com.aibook.repository.BookRepository;
import com.aibook.repository.BookVersionRepository;
import com.aibook.repository.BookmarkRepository;
import com.aibook.repository.ReadingProgressRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookVersionAggregationServiceTest {

    @Test
    void shouldAggregateCompatibleBooksAndKeepDifferentAuthorsSeparate() {
        User user = User.builder().id(7L).username("reader").build();
        Book primary = book(1L, " 三体 ", "刘慈欣", "epub", user);
        primary.setDescription("短简介");
        Book duplicate = book(2L, "扫描文件", null, "pdf", user);
        duplicate.setFilePath("/books/三体-刘慈欣-精校版.pdf");
        duplicate.setDescription("这是另一个版本中更完整的内容简介");
        duplicate.setIsFavorite(true);
        Book differentAuthor = book(3L, "三体", "其他作者", "txt", user);

        BookVersion primaryVersion = version(11L, primary, true);
        BookVersion duplicateVersion = version(12L, duplicate, true);
        BookVersion differentVersion = version(13L, differentAuthor, true);

        BookRepository bookRepository = mock(BookRepository.class);
        BookVersionRepository versionRepository = mock(BookVersionRepository.class);
        BookVersionService versionService = mock(BookVersionService.class);
        ReadingProgressRepository progressRepository =
                mock(ReadingProgressRepository.class);
        BookmarkRepository bookmarkRepository = mock(BookmarkRepository.class);
        BookHighlightRepository highlightRepository =
                mock(BookHighlightRepository.class);
        BookListRepository bookListRepository = mock(BookListRepository.class);

        when(bookRepository.findByUserAndDeletedAtIsNull(user))
                .thenReturn(List.of(primary, duplicate, differentAuthor));
        when(versionService.ensurePrimaryVersion(primary)).thenReturn(primaryVersion);
        when(versionService.ensurePrimaryVersion(duplicate)).thenReturn(duplicateVersion);
        when(versionService.ensurePrimaryVersion(differentAuthor)).thenReturn(differentVersion);
        when(versionRepository.findByBookOrderByPrimaryVersionDescCreatedAtAsc(duplicate))
                .thenReturn(List.of(duplicateVersion));
        when(progressRepository.findByUserAndBook(any(User.class), any(Book.class)))
                .thenReturn(Optional.empty());
        when(bookmarkRepository.findByBook(any(Book.class))).thenReturn(List.of());
        when(highlightRepository.findByBook(any(Book.class))).thenReturn(List.of());
        when(bookListRepository.findByUser(user)).thenReturn(List.of());

        BookVersionAggregationService service = new BookVersionAggregationService(
                bookRepository,
                versionRepository,
                versionService,
                progressRepository,
                bookmarkRepository,
                highlightRepository,
                bookListRepository);

        var result = service.rebuild(user);

        assertEquals(3, result.getScannedBooks());
        assertEquals(1, result.getRebuiltGroups());
        assertEquals(1, result.getMergedBooks());
        assertEquals(1, result.getAggregatedVersions());
        assertEquals(2, result.getRemainingBooks());
        assertSame(primary, duplicateVersion.getBook());
        assertFalse(duplicateVersion.getPrimaryVersion());
        assertNotNull(duplicate.getDeletedAt());
        assertNotNull(duplicate.getPurgedAt());
        assertEquals(
                "这是另一个版本中更完整的内容简介",
                primary.getDescription());
        assertEquals(true, primary.getIsFavorite());
        assertEquals(null, differentAuthor.getDeletedAt());
    }

    @Test
    void shouldAggregateHighlySimilarTitlesWhenAuthorMatches() {
        User user = User.builder().id(8L).username("reader-2").build();
        Book primary = book(21L, "深入理解计算机系统", "Randal Bryant", "epub", user);
        Book similar = book(22L, "深入理解计算机系統", "Randal Bryant", "pdf", user);
        BookVersion primaryVersion = version(31L, primary, true);
        BookVersion similarVersion = version(32L, similar, true);

        BookRepository bookRepository = mock(BookRepository.class);
        BookVersionRepository versionRepository = mock(BookVersionRepository.class);
        BookVersionService versionService = mock(BookVersionService.class);
        ReadingProgressRepository progressRepository =
                mock(ReadingProgressRepository.class);
        BookmarkRepository bookmarkRepository = mock(BookmarkRepository.class);
        BookHighlightRepository highlightRepository =
                mock(BookHighlightRepository.class);
        BookListRepository bookListRepository = mock(BookListRepository.class);

        when(bookRepository.findByUserAndDeletedAtIsNull(user))
                .thenReturn(List.of(primary, similar));
        when(versionService.ensurePrimaryVersion(primary)).thenReturn(primaryVersion);
        when(versionService.ensurePrimaryVersion(similar)).thenReturn(similarVersion);
        when(versionRepository.findByBookOrderByPrimaryVersionDescCreatedAtAsc(similar))
                .thenReturn(List.of(similarVersion));
        when(progressRepository.findByUserAndBook(any(User.class), any(Book.class)))
                .thenReturn(Optional.empty());
        when(bookmarkRepository.findByBook(any(Book.class))).thenReturn(List.of());
        when(highlightRepository.findByBook(any(Book.class))).thenReturn(List.of());
        when(bookListRepository.findByUser(user)).thenReturn(List.of());

        var result = new BookVersionAggregationService(
                bookRepository,
                versionRepository,
                versionService,
                progressRepository,
                bookmarkRepository,
                highlightRepository,
                bookListRepository)
                .rebuild(user);

        assertEquals(1, result.getMergedBooks());
        assertSame(primary, similarVersion.getBook());
    }

    private Book book(
            Long id,
            String title,
            String author,
            String format,
            User user) {
        return Book.builder()
                .id(id)
                .title(title)
                .author(author)
                .format(format)
                .filePath("/books/" + id + "." + format)
                .fileHash("hash-" + id)
                .user(user)
                .build();
    }

    private BookVersion version(Long id, Book book, boolean primary) {
        return BookVersion.builder()
                .id(id)
                .book(book)
                .displayName(book.getTitle() + "." + book.getFormat())
                .format(book.getFormat())
                .filePath(book.getFilePath())
                .fileHash(book.getFileHash())
                .primaryVersion(primary)
                .build();
    }
}
