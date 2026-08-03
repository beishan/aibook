package com.aibook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.Category;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.CategoryRepository;
import com.aibook.repository.UserRepository;
import org.junit.jupiter.api.Test;

class ScannedBookPersistenceServiceTest {

    @Test
    void resolvesAssociationsInsidePersistenceBoundary() {
        BookRepository bookRepository = mock(BookRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        CategoryRepository categoryRepository = mock(CategoryRepository.class);
        User userReference = User.builder().id(1L).build();
        Category categoryReference = Category.builder().id(2L).build();
        Book book = Book.builder()
                .title("测试书籍")
                .format("epub")
                .filePath("/scanfolder/test.epub")
                .build();

        when(userRepository.getReferenceById(1L)).thenReturn(userReference);
        when(categoryRepository.getReferenceById(2L)).thenReturn(categoryReference);
        when(bookRepository.save(book)).thenReturn(book);

        ScannedBookPersistenceService service = new ScannedBookPersistenceService(
                bookRepository,
                userRepository,
                categoryRepository,
                mock(OperationLogService.class));

        Book saved = service.save(book, 1L, 2L);

        assertThat(saved.getUser()).isSameAs(userReference);
        assertThat(saved.getCategory()).isSameAs(categoryReference);
        verify(bookRepository).save(book);
    }

    @Test
    void leavesCategoryEmptyWhenDirectoryHasNoDefault() {
        BookRepository bookRepository = mock(BookRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        CategoryRepository categoryRepository = mock(CategoryRepository.class);
        User userReference = User.builder().id(1L).build();
        Book book = Book.builder()
                .title("测试书籍")
                .format("epub")
                .filePath("/scanfolder/test.epub")
                .build();

        when(userRepository.getReferenceById(1L)).thenReturn(userReference);
        when(bookRepository.save(book)).thenReturn(book);

        ScannedBookPersistenceService service = new ScannedBookPersistenceService(
                bookRepository,
                userRepository,
                categoryRepository,
                mock(OperationLogService.class));

        Book saved = service.save(book, 1L, null);

        assertThat(saved.getUser()).isSameAs(userReference);
        assertThat(saved.getCategory()).isNull();
        verify(bookRepository).save(book);
    }
}
