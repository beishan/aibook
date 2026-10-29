package com.aibook.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aibook.model.entity.User;
import com.aibook.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BookCardBadgeSettingsServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final BookCardBadgeSettingsService service =
            new BookCardBadgeSettingsService(users, new ObjectMapper());

    @Test
    void defaultsAndSavedSettingsAreAccountScoped() {
        var first = new User();
        var second = new User();
        assertThat(service.get(first).abbreviations()).containsEntry("structured", "线");
        assertThat(service.get(first).transparency()).isEqualTo(30);
        service.save(first, new BookCardBadgeSettingsService.Settings(Map.of("structured", " 网 "), 60));
        assertThat(service.get(first).abbreviations()).containsEntry("structured", "网").containsEntry("epub", "E");
        assertThat(service.get(first).transparency()).isEqualTo(60);
        assertThat(service.get(second).abbreviations()).containsEntry("structured", "线");
        verify(users).save(first);
    }

    @Test
    void rejectsInvalidSettingsWithoutSaving() {
        for (var request : java.util.List.of(
                new BookCardBadgeSettingsService.Settings(Map.of("epub", ""), 30),
                new BookCardBadgeSettingsService.Settings(Map.of("epub", "12345"), 30),
                new BookCardBadgeSettingsService.Settings(Map.of("epub", "E\n"), 30),
                new BookCardBadgeSettingsService.Settings(Map.of("invalid", "X"), 30),
                new BookCardBadgeSettingsService.Settings(Map.of(), -1),
                new BookCardBadgeSettingsService.Settings(Map.of(), 101))) {
            assertThatThrownBy(() -> service.save(new User(), request))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(users);
    }

    @Test
    void corruptedStorageFallsBackAndUnicodeAndTransparencyLimitsPersist() {
        var user = new User();
        user.setBookCardBadgeSettings("invalid-json");
        assertThat(service.get(user).transparency()).isEqualTo(30);
        service.save(user, new BookCardBadgeSettingsService.Settings(Map.of("epub", "📖📖📖📖"), 0));
        assertThat(service.get(user).abbreviations()).containsEntry("epub", "📖📖📖📖");
        assertThat(service.get(user).transparency()).isZero();
        service.save(user, new BookCardBadgeSettingsService.Settings(Map.of(), 100));
        assertThat(service.get(user).transparency()).isEqualTo(100);
    }
}
