package com.aibook.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.util.BookSeriesMetadata;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class BookSeriesServiceTest {
    @Test
    void metadataPatchPreservesSeriesUnlessExplicitlyChangedAndDtoRoundTripsIt() {
        BookRepository repository = mock(BookRepository.class);
        User owner = User.builder().id(5L).build();
        Book book = book(1, "1.5");
        book.setSeriesName("系列");
        when(repository.findByIdAndUserAndDeletedAtIsNull(1L, owner)).thenReturn(java.util.Optional.of(book));
        BookService service = new BookService(repository,
                mock(com.aibook.repository.ReadingProgressRepository.class),
                mock(com.aibook.repository.BookmarkRepository.class),
                mock(com.aibook.repository.BookHighlightRepository.class),
                mock(com.aibook.repository.BookListRepository.class), mock(CategoryService.class),
                mock(TagService.class), mock(AuthorService.class), mock(OperationLogService.class));
        var dto = service.updateBookMetadata(1L,
                com.aibook.dto.BookDTO.builder().title("新书名").build(), owner);
        assertEquals("系列", dto.getSeriesName());
        assertEquals(new BigDecimal("1.5"), dto.getSeriesIndex());
        dto = service.updateBookMetadata(1L,
                com.aibook.dto.BookDTO.builder().seriesName("").build(), owner);
        assertNull(dto.getSeriesName());
        assertNull(dto.getSeriesIndex());
    }
    @Test
    void numericOrderKeepsUnknownVolumesLastAndSameVolumeStable() {
        BookRepository repository = mock(BookRepository.class);
        User user = User.builder().id(5L).build();
        when(repository.findSeriesBooks(user, "三体")).thenReturn(List.of(
                book(4, "10"), book(7, null), book(3, "1.5"), book(2, "1"), book(1, "0"), book(5, "1")));
        var result = new BookSeriesService(repository).books(user, "  三体  ");
        assertEquals(List.of(1L, 2L, 5L, 3L, 4L, 7L), result.stream().map(item -> item.id()).toList());
        verify(repository).findSeriesBooks(user, "三体");
    }

    @Test
    void listAndDetailAlwaysQueryAuthenticatedOwner() {
        BookRepository repository = mock(BookRepository.class);
        User owner = User.builder().id(5L).build();
        User other = User.builder().id(6L).build();
        when(repository.findSeriesBooks(owner, "同名系列")).thenReturn(List.of(book(1, "1")));
        when(repository.findSeriesBooks(other, "同名系列")).thenReturn(List.of());
        BookSeriesService service = new BookSeriesService(repository);
        assertEquals(1, service.books(owner, "同名系列").size());
        assertTrue(service.books(other, "同名系列").isEmpty());
        service.list(owner);
        verify(repository).findSeriesSummaries(owner, Book.ReadingStatus.FINISHED);
    }

    @Test
    void metadataSupportsPrequelsDecimalsAndRemovingSeries() {
        Book book = book(1, null);
        BookSeriesMetadata.apply(book, "  银河  帝国 ", new BigDecimal("1.50"));
        assertEquals("银河 帝国", book.getSeriesName());
        assertEquals(new BigDecimal("1.50"), book.getSeriesIndex());
        BookSeriesMetadata.apply(book, "银河帝国", BigDecimal.ZERO);
        assertEquals(BigDecimal.ZERO, book.getSeriesIndex());
        BookSeriesMetadata.apply(book, "银河帝国", null);
        assertNull(book.getSeriesIndex());
        BookSeriesMetadata.apply(book, "  ", BigDecimal.ONE);
        assertNull(book.getSeriesName());
        assertNull(book.getSeriesIndex());
    }

    @Test
    void invalidMetadataCannotBeSilentlyRoundedOrOverflowColumn() {
        Book book = book(1, "1");
        for (String value : List.of("-1", "10000", "1.001")) {
            assertThrows(ResponseStatusException.class,
                    () -> BookSeriesMetadata.apply(book, "系列", new BigDecimal(value)));
        }
        assertThrows(ResponseStatusException.class, () -> BookSeriesMetadata.apply(book, "名".repeat(121), null));
        assertThrows(ResponseStatusException.class, () -> BookSeriesMetadata.parseIndex("第一卷"));
        assertNull(BookSeriesMetadata.parseIndex(" "));
    }

    private Book book(long id, String index) {
        return Book.builder().id(id).title("卷册" + id).format("epub")
                .seriesIndex(index == null ? null : new BigDecimal(index)).build();
    }
}
