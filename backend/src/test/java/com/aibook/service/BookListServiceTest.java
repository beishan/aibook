package com.aibook.service;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookList;
import com.aibook.model.entity.User;
import com.aibook.repository.BookListRepository;
import com.aibook.repository.BookRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookListServiceTest {

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
