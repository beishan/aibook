package com.aibook.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aibook.dto.BookDTO;
import com.aibook.dto.BookMembershipRequest;
import com.aibook.dto.BookMembershipRequest.Action;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookList;
import com.aibook.model.entity.User;
import com.aibook.repository.BookListRepository;
import com.aibook.repository.BookRepository;
import jakarta.validation.Validation;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class BookMembershipServiceTest {

    private final BookRepository books = mock(BookRepository.class);
    private final BookListRepository lists = mock(BookListRepository.class);
    private final BookService bookService = mock(BookService.class);
    private final ShelfService shelfService = mock(ShelfService.class);
    private final User user = User.builder().id(1L).username("reader").build();
    private final Book first = Book.builder().id(1L).isFavorite(true).build();
    private final Book second = Book.builder().id(2L).isFavorite(false).build();
    private final BookMembershipService service =
            new BookMembershipService(books, lists, bookService, shelfService);

    private void ownedBooks() {
        when(books.findByIdInAndUser(List.of(1L, 2L), user)).thenReturn(List.of(first, second));
        when(bookService.convertToDTO(any(Book.class))).thenAnswer(invocation -> {
            Book book = invocation.getArgument(0);
            return BookDTO.builder().id(book.getId()).isFavorite(book.getIsFavorite()).build();
        });
    }

    @Test
    void favoritesAreAdditiveAndDeduplicateInput() {
        ownedBooks();
        var request = new BookMembershipRequest(List.of(1L, 2L, 1L), Action.FAVORITE, null);
        var result = service.add(request, user);
        service.add(request, user);
        assertEquals(2, result.size());
        assertTrue(first.getIsFavorite());
        assertTrue(second.getIsFavorite());
        verifyNoInteractions(shelfService, lists);
    }

    @Test
    void missingOrForeignBooksRejectBeforeAnyWrite() {
        when(books.findByIdInAndUser(List.of(1L, 2L), user)).thenReturn(List.of(first));
        assertThrows(ResponseStatusException.class, () -> service.add(
                new BookMembershipRequest(List.of(1L, 2L), Action.FAVORITE, null), user));
        verify(books, never()).saveAll(any());
        verifyNoInteractions(shelfService, lists);
    }

    @Test
    void shelfUsesExistingIdempotentServiceAndDefaultGroup() {
        ownedBooks();
        when(shelfService.addBook(1L, null, user)).thenReturn(BookDTO.builder().id(1L).build());
        when(shelfService.addBook(2L, null, user)).thenReturn(BookDTO.builder().id(2L).build());
        assertEquals(2, service.add(new BookMembershipRequest(
                List.of(1L, 2L, 1L), Action.SHELF, null), user).size());
        verify(shelfService).addBook(1L, null, user);
        verify(shelfService).addBook(2L, null, user);
        verifyNoInteractions(lists);
    }

    @Test
    void partiallyExistingListsAreFilledWithoutDuplicates() {
        ownedBooks();
        BookList list = BookList.builder().id(8L).books(new ArrayList<>(List.of(first))).build();
        when(lists.findByIdInAndUser(List.of(8L), user)).thenReturn(List.of(list));
        var request = new BookMembershipRequest(List.of(1L, 2L), Action.BOOK_LIST, List.of(8L, 8L));
        service.add(request, user);
        service.add(request, user);
        assertEquals(List.of(1L, 2L), list.getBooks().stream().map(Book::getId).toList());
        verify(books, never()).saveAll(any());
    }

    @Test
    void foreignListRejectsWholeSelectionBeforeModifyingAnyList() {
        ownedBooks();
        BookList own = BookList.builder().id(8L).build();
        when(lists.findByIdInAndUser(List.of(8L, 9L), user)).thenReturn(List.of(own));
        assertThrows(ResponseStatusException.class, () -> service.add(new BookMembershipRequest(
                List.of(1L, 2L), Action.BOOK_LIST, List.of(8L, 9L)), user));
        assertTrue(own.getBooks().isEmpty());
        verify(lists, never()).saveAll(any());
    }

    @Test
    void listActionRequiresTargets() {
        ownedBooks();
        assertThrows(ResponseStatusException.class, () -> service.add(new BookMembershipRequest(
                List.of(1L, 2L), Action.BOOK_LIST, null), user));
        verifyNoInteractions(lists);
    }

    @Test
    void requestValidatesLimitsAndIdentifiers() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(validator.validate(new BookMembershipRequest(List.of(), Action.SHELF, null)).isEmpty());
            assertFalse(validator.validate(new BookMembershipRequest(List.of(-1L), null, null)).isEmpty());
            assertFalse(validator.validate(new BookMembershipRequest(
                    java.util.Collections.nCopies(201, 1L), Action.SHELF, null)).isEmpty());
            assertFalse(validator.validate(new BookMembershipRequest(
                    List.of(1L), Action.BOOK_LIST, java.util.Collections.nCopies(21, 8L))).isEmpty());
            assertTrue(validator.validate(new BookMembershipRequest(
                    List.of(1L, 2L), Action.FAVORITE, null)).isEmpty());
        }
    }
}
