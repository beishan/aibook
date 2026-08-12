package com.aibook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.RandomBookCover;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.RandomBookCoverRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class RandomBookCoverServiceTest {

    @TempDir
    Path tempDirectory;

    private RandomBookCoverRepository coverRepository;
    private BookRepository bookRepository;
    private RandomBookCoverService service;
    private User user;

    @BeforeEach
    void setUp() throws Exception {
        coverRepository = Mockito.mock(RandomBookCoverRepository.class);
        bookRepository = Mockito.mock(BookRepository.class);
        service = new RandomBookCoverService(coverRepository, bookRepository);
        ReflectionTestUtils.setField(service, "uploadDir", tempDirectory.toString());
        ReflectionTestUtils.setField(service, "coverDir", "covers");
        Files.createDirectories(tempDirectory.resolve("covers"));
        user = User.builder().id(7L).username("reader").build();
    }

    @Test
    void assignsIndependentCopyFromUsersLibrary() throws Exception {
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        Files.write(tempDirectory.resolve("covers/library.png"), png);
        RandomBookCover libraryCover = RandomBookCover.builder()
                .id(3L)
                .user(user)
                .storedFilename("library.png")
                .originalFilename("library.png")
                .contentType("image/png")
                .fileSize((long) png.length)
                .build();
        Book book = Book.builder().id(11L).title("测试书籍").user(user).build();
        when(coverRepository.findAllByUserOrderByCreatedAtDesc(user))
                .thenReturn(List.of(libraryCover));
        when(bookRepository.save(book)).thenReturn(book);

        Book updated = service.assign(book, user);

        assertThat(updated.getCoverUrl()).startsWith("covers/random-book-").endsWith(".png");
        Path assigned = tempDirectory.resolve(updated.getCoverUrl());
        assertThat(assigned).exists().hasBinaryContent(png);
        assertThat(assigned.getFileName().toString()).isNotEqualTo("library.png");
        verify(bookRepository).save(book);
    }

    @Test
    void reportsEmptyLibraryForManualRandomization() {
        Book book = Book.builder().id(11L).title("测试书籍").user(user).build();
        when(coverRepository.findAllByUserOrderByCreatedAtDesc(user)).thenReturn(List.of());

        assertThatThrownBy(() -> service.assign(book, user))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("封面库为空");
    }

    @Test
    void keepsExistingCoverDuringAutomaticAssignment() {
        Book book = Book.builder()
                .id(11L)
                .title("测试书籍")
                .user(user)
                .coverUrl("covers/existing.jpg")
                .build();

        assertThat(service.assignIfMissing(book, user)).isSameAs(book);
        verify(coverRepository, never()).findAllByUserOrderByCreatedAtDesc(user);
        verify(bookRepository, never()).save(book);
    }
}
