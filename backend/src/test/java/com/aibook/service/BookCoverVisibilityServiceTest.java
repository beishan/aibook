package com.aibook.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aibook.dto.CoverPrivacyScopeDTO;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class BookCoverVisibilityServiceTest {
    @Test
    void storesShowAndHideOnTheOwnedBookWithoutChangingGlobalDefaultsOrCover() {
        var books = mock(BookRepository.class);
        var users = mock(UserRepository.class);
        var service = new CoverPrivacyService(users, new ObjectMapper(), books);
        var user = User.builder().id(1L).build();
        user.setAllBookCoversHidden(true);
        var book = Book.builder().id(7L).title("凡人修仙传").coverUrl("covers/original.jpg").user(user).build();
        when(books.findByIdAndUserAndDeletedAtIsNull(7L, user)).thenReturn(Optional.of(book));
        assertThat(service.updateBookCoverVisibility(user, 7L, false).hidden()).isFalse();
        assertThat(book.getCoverHidden()).isFalse();
        assertThat(service.updateBookCoverVisibility(user, 7L, true).hidden()).isTrue();
        assertThat(book.getCoverHidden()).isTrue();
        assertThat(user.getAllBookCoversHidden()).isTrue();
        assertThat(book.getCoverUrl()).isEqualTo("covers/original.jpg");
        verify(books, times(2)).save(book);
        verifyNoInteractions(users);
        service.updateBookCoverVisibility(user, 7L, null);
        assertThat(book.getCoverHidden()).isNull();
    }

    @Test
    void rejectsOtherOwnersOrDeletedBooks() {
        var books = mock(BookRepository.class);
        var service = new CoverPrivacyService(mock(UserRepository.class), new ObjectMapper(), books);
        var user = User.builder().id(2L).build();
        when(books.findByIdAndUserAndDeletedAtIsNull(7L, user)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateBookCoverVisibility(user, 7L, false))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        verify(books, never()).save(any());
    }

    @Test
    void accountSettingsUpdatesCannotOverwriteBookLevelSettingsAndHydrationReturnsBothStates() {
        var books = mock(BookRepository.class);
        var users = mock(UserRepository.class);
        var service = new CoverPrivacyService(users, new ObjectMapper(), books);
        var user = User.builder().id(1L).build();
        user.setAllBookCoversHidden(false);
        var show = mock(BookRepository.CoverVisibility.class);
        var hide = mock(BookRepository.CoverVisibility.class);
        when(show.getId()).thenReturn(7L);
        when(show.getHidden()).thenReturn(false);
        when(hide.getId()).thenReturn(8L);
        when(hide.getHidden()).thenReturn(true);
        when(books.findCoverVisibilityOverrides(user)).thenReturn(List.of(show, hide));
        when(users.save(user)).thenReturn(user);
        var result = service.updateBookCoverSettings(user,
                new CoverPrivacyScopeDTO(true, true, Map.of(), Map.of(7L, true)));
        assertThat(result.allHidden()).isTrue();
        assertThat(result.bookOverrides()).containsEntry(7L, false).containsEntry(8L, true);
        verify(books, never()).save(any());
        assertThat(service.getBookCoverSettings(user).bookOverrides()).isEqualTo(result.bookOverrides());
    }
}
