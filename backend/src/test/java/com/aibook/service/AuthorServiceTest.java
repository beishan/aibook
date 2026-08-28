package com.aibook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibook.dto.AuthorRequest;
import com.aibook.model.entity.Author;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.User;
import com.aibook.repository.AuthorRepository;
import com.aibook.repository.BookRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AuthorServiceTest {

    private AuthorRepository authorRepository;
    private BookRepository bookRepository;
    private AuthorService service;
    private User user;

    @BeforeEach
    void setUp() {
        authorRepository = mock(AuthorRepository.class);
        bookRepository = mock(BookRepository.class);
        service = new AuthorService(authorRepository, bookRepository);
        user = User.builder().id(7L).username("reader").build();
    }

    @Test
    void synchronizesRecognizedMultipleAuthorsAndLinksBook() {
        Book book = Book.builder().id(1L).title("测试书").author("鲁迅、 周作人").user(user).build();
        Author luXun = author(11L, "鲁迅", "鲁迅");
        Author zhou = author(12L, "周作人", "周作人");
        when(authorRepository.findByUserAndNormalizedName(user, "鲁迅"))
                .thenReturn(Optional.of(luXun));
        when(authorRepository.findByUserAndNormalizedName(user, "周作人"))
                .thenReturn(Optional.of(zhou));

        service.synchronizeBook(book);

        assertThat(book.getAuthors()).containsExactly(luXun, zhou);
        verify(bookRepository).save(book);
    }

    @Test
    void ignoresUnknownAuthorPlaceholder() {
        Book book = Book.builder().id(1L).title("测试书").author("未知作者").user(user).build();

        service.synchronizeBook(book);

        assertThat(book.getAuthors()).isEmpty();
        verify(authorRepository, never()).insertIfAbsent(any(), any(), any());
        verify(bookRepository, never()).save(any());
    }

    @Test
    void manuallyCreatesNormalizedAuthor() {
        AuthorRequest request = new AuthorRequest();
        request.setName("  Ursula   Le Guin  ");
        Author created = author(13L, "Ursula Le Guin", "ursula le guin");
        when(authorRepository.insertIfAbsent("Ursula Le Guin", "ursula le guin", 7L))
                .thenReturn(1);
        when(authorRepository.findByUserAndNormalizedName(user, "ursula le guin"))
                .thenReturn(Optional.of(created));
        when(authorRepository.countActiveBooks(13L)).thenReturn(0L);

        assertThat(service.createAuthor(user, request).getName()).isEqualTo("Ursula Le Guin");
    }

    @Test
    void rejectsDuplicateManualAuthor() {
        AuthorRequest request = new AuthorRequest();
        request.setName("鲁迅");
        when(authorRepository.insertIfAbsent("鲁迅", "鲁迅", 7L)).thenReturn(0);

        assertThatThrownBy(() -> service.createAuthor(user, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("作者已存在");
    }

    private Author author(long id, String name, String normalizedName) {
        return Author.builder()
                .id(id)
                .name(name)
                .normalizedName(normalizedName)
                .user(user)
                .build();
    }
}
