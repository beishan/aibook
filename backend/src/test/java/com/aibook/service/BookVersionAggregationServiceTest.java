package com.aibook.service;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookVersion;
import com.aibook.model.entity.User;
import com.aibook.repository.BookHighlightRepository;
import com.aibook.repository.BookListRepository;
import com.aibook.repository.BookRepository;
import com.aibook.repository.BookVersionRepository;
import com.aibook.repository.BookVersionIdentityProjection;
import com.aibook.repository.BookmarkRepository;
import com.aibook.repository.BookScanSourceRepository;
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
    void shouldAggregateOnePairWithoutHoldingTheWholeLibrary() {
        User user = User.builder().id(7L).username("reader").build();
        Book primary = book(1L, " 三体 ", "刘慈欣", "epub", user);
        primary.setDescription("短简介");
        Book duplicate = book(2L, "扫描文件", null, "pdf", user);
        duplicate.setFilePath("/books/三体-刘慈欣-精校版.pdf");
        duplicate.setDescription("这是另一个版本中更完整的内容简介");
        duplicate.setIsFavorite(true);
        duplicate.setSeriesName("地球往事");
        duplicate.setSeriesIndex(java.math.BigDecimal.ONE);

        BookVersion primaryVersion = version(11L, primary, true);
        BookVersion duplicateVersion = version(12L, duplicate, true);

        BookRepository bookRepository = mock(BookRepository.class);
        BookVersionRepository versionRepository = mock(BookVersionRepository.class);
        BookVersionService versionService = mock(BookVersionService.class);
        ReadingProgressRepository progressRepository =
                mock(ReadingProgressRepository.class);
        BookmarkRepository bookmarkRepository = mock(BookmarkRepository.class);
        BookHighlightRepository highlightRepository =
                mock(BookHighlightRepository.class);
        BookListRepository bookListRepository = mock(BookListRepository.class);

        when(bookRepository.findByIdInAndUser(List.of(1L, 2L), user))
                .thenReturn(List.of(primary, duplicate));
        when(versionService.ensurePrimaryVersion(primary)).thenReturn(primaryVersion);
        when(versionService.ensurePrimaryVersion(duplicate)).thenReturn(duplicateVersion);
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
                bookListRepository,
                mock(BookScanSourceRepository.class));

        int aggregatedVersions = service.aggregatePair(1L, 2L, user);

        assertEquals(1, aggregatedVersions);
        assertEquals("地球往事", primary.getSeriesName());
        assertEquals(java.math.BigDecimal.ONE, primary.getSeriesIndex());
        assertSame(primary, duplicateVersion.getBook());
        assertFalse(duplicateVersion.getPrimaryVersion());
        assertNotNull(duplicate.getDeletedAt());
        assertNotNull(duplicate.getPurgedAt());
        assertEquals(
                "这是另一个版本中更完整的内容简介",
                primary.getDescription());
        assertEquals(true, primary.getIsFavorite());
    }

    @Test
    void shouldBuildLightweightPlanFromFilenameSimilarityAndAuthorRules() {
        BookRepository bookRepository = mock(BookRepository.class);
        BookVersionRepository versionRepository = mock(BookVersionRepository.class);
        BookVersionService versionService = mock(BookVersionService.class);
        ReadingProgressRepository progressRepository =
                mock(ReadingProgressRepository.class);
        BookmarkRepository bookmarkRepository = mock(BookmarkRepository.class);
        BookHighlightRepository highlightRepository =
                mock(BookHighlightRepository.class);
        BookListRepository bookListRepository = mock(BookListRepository.class);

        List<BookVersionIdentityProjection> projections = List.of(
                projection(21L, "三体", "刘慈欣", "/books/三体.epub"),
                projection(22L, "扫描文件", null, "/books/三体-刘慈欣-精校版.txt"),
                projection(23L, "三体", "其他作者", "/books/另一位作者的三体.txt"),
                projection(
                        24L,
                        "深入理解计算机系统",
                        "Randal Bryant",
                        "/books/csapp.epub"),
                projection(
                        25L,
                        "深入理解计算机系統",
                        "Randal Bryant",
                        "/books/csapp.pdf"));
        when(bookRepository.findVersionIdentitiesByUserId(8L))
                .thenReturn(projections);

        var plan = new BookVersionAggregationService(
                bookRepository,
                versionRepository,
                versionService,
                progressRepository,
                bookmarkRepository,
                highlightRepository,
                bookListRepository,
                mock(BookScanSourceRepository.class))
                .buildPlan(8L);

        assertEquals(5, plan.totalBooks());
        assertEquals(
                List.of(21L, 22L),
                plan.groups().stream()
                        .filter(group -> group.bookIds().contains(21L))
                        .findFirst()
                        .orElseThrow()
                        .bookIds());
        assertEquals(
                List.of(24L, 25L),
                plan.groups().stream()
                        .filter(group -> group.bookIds().contains(24L))
                        .findFirst()
                        .orElseThrow()
                        .bookIds());
        assertEquals(
                List.of(23L),
                plan.groups().stream()
                        .filter(group -> group.bookIds().contains(23L))
                        .findFirst()
                        .orElseThrow()
                        .bookIds());
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

    private BookVersionIdentityProjection projection(
            Long id, String title, String author, String filePath) {
        BookVersionIdentityProjection projection =
                mock(BookVersionIdentityProjection.class);
        when(projection.getId()).thenReturn(id);
        when(projection.getTitle()).thenReturn(title);
        when(projection.getAuthor()).thenReturn(author);
        when(projection.getFilePath()).thenReturn(filePath);
        return projection;
    }
}
