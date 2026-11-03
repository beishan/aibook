package com.aibook.service;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookList;
import com.aibook.model.entity.User;
import com.aibook.repository.BookListRepository;
import com.aibook.repository.BookRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

class BookListServiceTest {

    @Test
    void paginatesOnlyOwnedListAndClampsPageSize() {
        BookListRepository lists = mock(BookListRepository.class);
        BookRepository books = mock(BookRepository.class);
        User user = User.builder().id(1L).build();
        BookList list = BookList.builder().id(8L).user(user).name("喜欢的书").build();
        Book book = Book.builder().id(5L).title("书名").author("作者").format("EPUB").build();
        when(lists.findByIdAndUser(8L, user)).thenReturn(Optional.of(list));
        when(books.findBookListPage(eq(8L), eq(user), any(Pageable.class)))
                .thenAnswer(call -> new PageImpl<>(List.of(book), call.getArgument(2), 101));

        var result = new BookListService(lists, books).getBookListPage(8L, user, 0, 500);

        assertEquals("喜欢的书", result.name());
        assertEquals(101, result.books().getTotalElements());
        assertEquals(100, result.books().getSize());
        assertEquals("书名", result.books().getContent().getFirst().title());
        verify(books).findBookListPage(eq(8L), eq(user), argThat(page -> page.getPageSize() == 100));

        User other = User.builder().id(2L).build();
        assertThrows(ResponseStatusException.class,
                () -> new BookListService(lists, books).getBookListPage(8L, other, 0, 24));
        verify(books, never()).findBookListPage(eq(8L), eq(other), any());
    }

    @Test
    void loadsBookMembersWhenListingBookLists() {
        BookListRepository bookListRepository = mock(BookListRepository.class);
        BookRepository bookRepository = mock(BookRepository.class);
        User user = mock(User.class);
        BookList bookList = mock(BookList.class);
        @SuppressWarnings("unchecked")
        List<Book> books = mock(List.class);
        when(bookListRepository.findByUser(user)).thenReturn(List.of(bookList));
        when(bookList.getBooks()).thenReturn(books);

        BookListService service = new BookListService(bookListRepository, bookRepository);

        List<BookList> result = service.getBookLists(user);

        assertEquals(List.of(bookList), result);
        verify(books).size();
    }
}
