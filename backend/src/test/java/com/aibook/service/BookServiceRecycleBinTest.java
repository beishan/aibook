package com.aibook.service;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.ShelfGroup;
import com.aibook.model.entity.User;
import com.aibook.repository.BookHighlightRepository;
import com.aibook.repository.BookListRepository;
import com.aibook.repository.BookRepository;
import com.aibook.repository.BookmarkRepository;
import com.aibook.repository.ReadingProgressRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookServiceRecycleBinTest {

    @TempDir
    Path tempDir;

    @Test
    void moveRestoreAndPurgeNeverDeleteOriginalFile() throws Exception {
        Path original = Files.writeString(tempDir.resolve("original.epub"), "book");
        User user = User.builder().id(1L).username("admin").build();
        ShelfGroup shelfGroup = ShelfGroup.builder().id(4L).user(user).name("收藏夹").build();
        Book book = Book.builder()
                .id(9L)
                .title("测试书籍")
                .format("epub")
                .filePath(original.toString())
                .fileHash("hash-9")
                .user(user)
                .onShelf(true)
                .shelfGroup(shelfGroup)
                .shelfAddedAt(java.time.LocalDateTime.now())
                .shelfSortOrder(0)
                .build();
        BookRepository bookRepository = mock(BookRepository.class);
        ReadingProgressRepository progressRepository = mock(ReadingProgressRepository.class);
        BookmarkRepository bookmarkRepository = mock(BookmarkRepository.class);
        BookHighlightRepository highlightRepository = mock(BookHighlightRepository.class);
        BookListRepository bookListRepository = mock(BookListRepository.class);
        BookService service = new BookService(
                bookRepository,
                progressRepository,
                bookmarkRepository,
                highlightRepository,
                bookListRepository,
                mock(CategoryService.class),
                mock(TagService.class),
                mock(OperationLogService.class));

        when(bookRepository.findByIdAndUserAndDeletedAtIsNull(9L, user))
                .thenReturn(Optional.of(book));
        service.deleteBook(9L, user);

        assertThat(book.getDeletedAt()).isNotNull();
        assertThat(Files.exists(original)).isTrue();

        when(bookRepository.findTrashByIds(List.of(9L), user)).thenReturn(List.of(book));
        service.restoreBooks(List.of(9L), user);

        assertThat(book.getDeletedAt()).isNull();
        assertThat(Files.exists(original)).isTrue();

        book.setDeletedAt(java.time.LocalDateTime.now());
        service.permanentlyDeleteBooks(List.of(9L), user);

        assertThat(book.getPurgedAt()).isNotNull();
        assertThat(book.getOnShelf()).isFalse();
        assertThat(book.getShelfGroup()).isNull();
        assertThat(Files.exists(original)).isTrue();
        verify(progressRepository).deleteByBook(book);
        verify(bookmarkRepository).deleteByBook(book);
        verify(highlightRepository).deleteByBook(book);
        verify(bookListRepository).deleteBookAssociations(9L);
        verify(bookRepository, never()).delete(book);
    }
}
