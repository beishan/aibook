package com.aibook.service.crawler;

import com.aibook.library.LibraryService;
import com.aibook.model.entity.CrawlerBook;
import com.aibook.model.entity.User;
import com.aibook.repository.CrawlerBookRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CrawlerImportLibrariesServiceTest {
    private final User user = User.builder().id(1L).build();
    private final CrawlerBookRepository books = mock(CrawlerBookRepository.class);
    private final LibraryService libraries = mock(LibraryService.class);
    private final CrawlerImportLibrariesService service = new CrawlerImportLibrariesService(books, libraries);

    @Test
    void savesDistinctTargetsAndEnablesImportUsingOwnerScopedUpdate() {
        when(books.findForLibraryImport(3L, user)).thenReturn(Optional.of(new CrawlerBook()));
        when(books.updateImportLibraries(3L, user, "7,8", true)).thenReturn(1);
        assertThat(service.save(user, 3L, List.of(8L, 7L, 8L))).containsExactly(7L, 8L);
        verify(libraries).library(user, 7L);
        verify(libraries).library(user, 8L);
        verify(books).updateImportLibraries(3L, user, "7,8", true);
    }

    @Test
    void clearingTargetsPreservesImportAndDoesNotRemoveExistingMembership() {
        when(books.findForLibraryImport(3L, user)).thenReturn(Optional.of(
                CrawlerBook.builder().autoImportEnabled(true).build()));
        when(books.updateImportLibraries(3L, user, "", true)).thenReturn(1);
        assertThat(service.save(user, 3L, List.of())).isEmpty();
        verifyNoInteractions(libraries);
    }

    @Test
    void rejectsMissingBookAndForeignLibraryBeforeSaving() {
        assertThatThrownBy(() -> service.save(user, 3L, List.of(7L)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("采集书籍不存在");
        when(books.findForLibraryImport(3L, user)).thenReturn(Optional.of(new CrawlerBook()));
        when(libraries.library(user, 7L)).thenThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "图书馆不存在"));
        assertThatThrownBy(() -> service.save(user, 3L, List.of(7L)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("图书馆不存在");
        verify(books, never()).updateImportLibraries(any(), any(), any(), anyBoolean());
    }

    @Test
    void rejectsInvalidOrOversizedTargets() {
        assertThatThrownBy(() -> service.save(user, 3L, null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.save(user, 3L, List.of(-1L))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.save(user, 3L,
                java.util.stream.LongStream.rangeClosed(1, 21).boxed().toList()))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(books, libraries);
    }

    @Test
    void readsPersistedTargetsOnlyForCurrentOwner() {
        when(books.findByIdAndSiteUser(3L, user)).thenReturn(Optional.of(
                CrawlerBook.builder().autoImportLibraryIds("8,7,8").build()));
        assertThat(service.get(user, 3L)).containsExactly(7L, 8L);
        assertThatThrownBy(() -> service.get(user, 4L)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void flushesImportedBookBeforeAddingToMultipleLibrariesAndAcceptsExistingMembership() {
        when(libraries.add(user, List.of(7L, 8L), List.of(9L), null)).thenReturn(List.of(
                new LibraryService.BatchItem(7L, 9L, "added", ""),
                new LibraryService.BatchItem(8L, 9L, "existing", "")));
        service.addCompleted(user, 9L, List.of(7L, 8L));
        var order = inOrder(books, libraries);
        order.verify(books).flush();
        order.verify(libraries).add(user, List.of(7L, 8L), List.of(9L), null);
    }

    @Test
    void reportsDeletedDestinationAsFailure() {
        when(libraries.add(user, List.of(7L), List.of(9L), null)).thenReturn(List.of(
                new LibraryService.BatchItem(7L, 9L, "failed", "不可用")));
        assertThatThrownBy(() -> service.addCompleted(user, 9L, List.of(7L)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("自动加入图书馆失败");
    }
}
