package com.aibook.service;

import com.aibook.dto.UserPreferencesDTO;
import com.aibook.model.entity.User;
import com.aibook.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceTest {

    @Test
    void updatesOnlyProvidedPreferences() {
        UserRepository repository = mock(UserRepository.class);
        User user = User.builder()
                .username("reader")
                .email("reader@example.com")
                .password("encoded")
                .webTheme("warm")
                .libraryViewMode("card")
                .build();
        when(repository.findByUsername("reader")).thenReturn(Optional.of(user));
        when(repository.save(user)).thenReturn(user);
        UserService service = new UserService(repository);

        UserPreferencesDTO result = service.updatePreferences(
                "reader",
                UserPreferencesDTO.builder().libraryViewMode("list").build());

        assertEquals("warm", result.getTheme());
        assertEquals("list", result.getLibraryViewMode());
    }

    @Test
    void rejectsUnsupportedPreferenceValues() {
        UserRepository repository = mock(UserRepository.class);
        User user = User.builder()
                .username("reader")
                .email("reader@example.com")
                .password("encoded")
                .build();
        when(repository.findByUsername("reader")).thenReturn(Optional.of(user));
        UserService service = new UserService(repository);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.updatePreferences(
                        "reader",
                        UserPreferencesDTO.builder().theme("unknown").build()));
    }
}
