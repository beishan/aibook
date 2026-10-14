package com.aibook.service;

import com.aibook.dto.SimilarBookDTO;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class BookSimilarityScanServiceTest {
    @Test
    void scansTheWholeAccountLibraryReadOnlyAndPaginatesResults() {
        BookRepository repository = mock(BookRepository.class);
        User user = User.builder().id(1L).username("reader").build();
        List<SimilarBookDTO> books = new ArrayList<>();
        for (int index = 0; index < 25; index++) {
            String title = "测试书籍" + index;
            books.add(new SimilarBookDTO(index * 2L + 1, title, "作者", "epub"));
            books.add(new SimilarBookDTO(index * 2L + 2, title + "精校版", "作者", "pdf"));
        }
        when(repository.findSimilarityScanBooks(user)).thenReturn(books);
        var service = new BookSimilarityScanService(repository);
        try {
            var started = service.start(user);
            await().atMost(Duration.ofSeconds(5)).until(() ->
                    service.get(started.taskId(), user, 0, 20).status().equals("COMPLETED"));
            var first = service.get(started.taskId(), user, 0, 20);
            var second = service.get(started.taskId(), user, 1, 20);

            assertEquals(50, first.totalBooks());
            assertEquals(50, first.processedBooks());
            assertEquals(50, first.matchedBooks());
            assertEquals(25, first.totalGroups());
            assertEquals(20, first.groups().size());
            assertEquals(5, second.groups().size());
            assertEquals(0, service.get(started.taskId(), user, Integer.MAX_VALUE, 20).groups().size());
            verify(repository).findSimilarityScanBooks(user);
            verifyNoMoreInteractions(repository);

            User other = User.builder().id(2L).username("other").build();
            assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                    () -> service.get(started.taskId(), other, 0, 20)).getStatusCode());
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                    () -> service.get(started.taskId(), user, -1, 20)).getStatusCode());
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                    () -> service.get(started.taskId(), user, 0, 51)).getStatusCode());
        } finally {
            service.shutdown();
        }
    }

    @Test
    void reusesAnActiveTaskForTheSameAccount() {
        BookRepository repository = mock(BookRepository.class);
        User user = User.builder().id(1L).username("reader").build();
        CountDownLatch release = new CountDownLatch(1);
        when(repository.findSimilarityScanBooks(user)).thenAnswer(invocation -> {
            release.await();
            return List.of();
        });
        var service = new BookSimilarityScanService(repository);
        try {
            var first = service.start(user);
            assertEquals(first.taskId(), service.start(user).taskId());
            release.countDown();
            await().atMost(Duration.ofSeconds(5)).until(() ->
                    service.get(first.taskId(), user, 0, 20).status().equals("COMPLETED"));
            verify(repository).findSimilarityScanBooks(user);
        } finally {
            release.countDown();
            service.shutdown();
        }
    }

    @Test
    void reportsFailuresWithoutExposingInternalErrors() {
        BookRepository repository = mock(BookRepository.class);
        User user = User.builder().id(1L).username("reader").build();
        when(repository.findSimilarityScanBooks(user)).thenThrow(new IllegalStateException("internal DB detail"));
        var service = new BookSimilarityScanService(repository);
        try {
            var task = service.start(user);
            await().atMost(Duration.ofSeconds(5)).until(() ->
                    service.get(task.taskId(), user, 0, 20).status().equals("FAILED"));
            assertEquals("扫描失败，请稍后重新扫描", service.get(task.taskId(), user, 0, 20).message());
        } finally {
            service.shutdown();
        }
    }
}
