package com.aibook.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aibook.model.entity.User;
import com.aibook.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class BookCoverPlaceholderServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final BookCoverPlaceholderService service = new BookCoverPlaceholderService(users, new ObjectMapper());

    @Test
    void persistsAllModesAndKeepsAccountsSeparate() {
        var first = new User();
        var second = new User();
        assertThat(service.get(first)).isEqualTo(new BookCoverPlaceholderService.Settings("PREFIX", 2, "书"));
        for (String mode : java.util.List.of("PREFIX", "FULL", "CUSTOM")) {
            var request = new BookCoverPlaceholderService.Settings(mode, 20, " 阅读 ");
            service.save(first, request);
            assertThat(service.get(first)).isEqualTo(new BookCoverPlaceholderService.Settings(mode, 20, "阅读"));
        }
        assertThat(service.get(second).length()).isEqualTo(2);
        assertThat(second.getBookCoverPlaceholderSettings()).isNull();
        verify(users, times(3)).save(first);
    }

    @Test
    void rejectsInvalidInputBeforeWriting() {
        for (var request : java.util.List.of(
                new BookCoverPlaceholderService.Settings("invalid", 2, "书"),
                new BookCoverPlaceholderService.Settings("PREFIX", 0, "书"),
                new BookCoverPlaceholderService.Settings("PREFIX", 21, "书"),
                new BookCoverPlaceholderService.Settings("PREFIX", 2, " "),
                new BookCoverPlaceholderService.Settings("CUSTOM", 2, "x".repeat(41)),
                new BookCoverPlaceholderService.Settings("CUSTOM", 2, "x\n"))) {
            assertThatThrownBy(() -> service.save(new User(), request)).isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(users);
    }

    @Test
    void corruptedStorageFallsBackAndUnicodeTextIsSupported() {
        var user = new User();
        user.setBookCoverPlaceholderSettings("broken");
        assertThat(service.get(user).length()).isEqualTo(2);
        var request = new BookCoverPlaceholderService.Settings("CUSTOM", 1, "𠮷".repeat(40));
        service.save(user, request);
        assertThat(service.get(user)).isEqualTo(request);
    }
}
