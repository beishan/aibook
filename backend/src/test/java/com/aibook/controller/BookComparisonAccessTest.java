package com.aibook.controller;

import com.aibook.dto.BookComparisonDTO;
import com.aibook.dto.BookDTO;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookVersion;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import java.util.Optional;
import com.aibook.service.BookComparisonService;
import com.aibook.service.BookService;
import com.aibook.service.BookVersionService;
import com.aibook.service.UserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BookComparisonAccessTest {
    @Mock BookService bookService;
    @Mock BookRepository bookRepository;
    @Mock UserService userService;
    @Mock BookVersionService bookVersionService;
    @Mock BookComparisonService bookComparisonService;
    @InjectMocks BookController controller;
    private MockMvc mvc;
    private final User user = User.builder().id(1L).username("reader").build();
    private final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("reader", null);

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
        when(userService.findByUsername("reader")).thenReturn(user);
    }

    @Test
    void foreignBooksAreRejectedBeforeReadingAnyFile() throws Exception {
        when(bookRepository.findByIdAndUserAndDeletedAtIsNull(2L, user)).thenReturn(Optional.empty());
        mvc.perform(get("/api/books/2/comparison").principal(auth)).andExpect(status().isNotFound());
        mvc.perform(get("/api/books/2/comparison/chapters/0").principal(auth)).andExpect(status().isNotFound());
        verifyNoInteractions(bookComparisonService, bookVersionService);
    }

    @Test
    void rejectsVersionsThatDoNotBelongToTheBook() throws Exception {
        Book book = Book.builder().id(1L).build();
        when(bookRepository.findByIdAndUserAndDeletedAtIsNull(1L, user)).thenReturn(Optional.of(book));
        when(bookVersionService.resolveVersion(book, 99L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        mvc.perform(get("/api/books/1/comparison").param("versionId", "99").principal(auth)).andExpect(status().isNotFound());
        verifyNoInteractions(bookComparisonService);
    }

    @Test
    void forwardsTheOwnedBookAndExplicitVersion() throws Exception {
        Book book = Book.builder().id(1L).build();
        BookVersion version = BookVersion.builder().id(11L).format("txt").build();
        when(bookRepository.findByIdAndUserAndDeletedAtIsNull(1L, user)).thenReturn(Optional.of(book));
        when(bookVersionService.resolveVersion(book, 11L)).thenReturn(version);
        when(bookComparisonService.describe(book, version)).thenReturn(new BookComparisonDTO(
                BookDTO.builder().id(1L).title("测试书籍").build(), 11L, "txt", 100L, true, "", List.of()));
        mvc.perform(get("/api/books/1/comparison").param("versionId", "11").principal(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("versionId").value(11));
        when(bookComparisonService.chapter(version, 0)).thenReturn(new BookComparisonDTO.Chapter(0, "第一章", "正文", 2, false));
        mvc.perform(get("/api/books/1/comparison/chapters/0").param("versionId", "11").principal(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("content").value("正文"));
    }
}
