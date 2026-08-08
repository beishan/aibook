package com.aibook.service;

import com.aibook.dto.UserPreferencesDTO;
import com.aibook.model.entity.User;
import com.aibook.model.entity.FontAsset;
import com.aibook.repository.FontAssetRepository;
import com.aibook.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
                UserPreferencesDTO.builder()
                        .libraryViewMode("compact-card")
                        .libraryPageSize(36)
                        .build());

        assertEquals("warm", result.getTheme());
        assertEquals("compact-card", result.getLibraryViewMode());
        assertEquals(36, result.getLibraryPageSize());
        assertEquals(2, result.getScanThreadCount());
        assertEquals(58, result.getDockSize());
        assertEquals(72, result.getDockOpacity());
        assertEquals(128, result.getDockMagnification());
        assertEquals(24, result.getDockBlur());
    }

    @Test
    void updatesDockAppearancePreferences() {
        UserRepository repository = mock(UserRepository.class);
        User user = User.builder()
                .username("reader")
                .email("reader@example.com")
                .password("encoded")
                .build();
        when(repository.findByUsername("reader")).thenReturn(Optional.of(user));
        when(repository.save(user)).thenReturn(user);
        UserService service = new UserService(repository);

        UserPreferencesDTO result = service.updatePreferences(
                "reader",
                UserPreferencesDTO.builder()
                        .dockSize(64)
                        .dockOpacity(66)
                        .dockMagnification(136)
                        .dockBlur(30)
                        .build());

        assertEquals(64, result.getDockSize());
        assertEquals(66, result.getDockOpacity());
        assertEquals(136, result.getDockMagnification());
        assertEquals(30, result.getDockBlur());
    }

    @Test
    void rejectsDockAppearanceOutsideAllowedRange() {
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
                        "reader", UserPreferencesDTO.builder().dockSize(80).build()));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.updatePreferences(
                        "reader", UserPreferencesDTO.builder().dockOpacity(20).build()));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.updatePreferences(
                        "reader", UserPreferencesDTO.builder().dockMagnification(170).build()));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.updatePreferences(
                        "reader", UserPreferencesDTO.builder().dockBlur(4).build()));
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

    @Test
    void rejectsScanThreadCountOutsideAllowedRange() {
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
                        UserPreferencesDTO.builder().scanThreadCount(0).build()));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.updatePreferences(
                        "reader",
                        UserPreferencesDTO.builder().scanThreadCount(17).build()));
    }

    @Test
    void rejectsUnsupportedLibraryPageSize() {
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
                        UserPreferencesDTO.builder().libraryPageSize(25).build()));
    }

    @Test
    void distinguishesOmittedFontFromExplicitNull() throws Exception {
        UserRepository repository = mock(UserRepository.class);
        FontAssetRepository fonts = mock(FontAssetRepository.class);
        User user = User.builder()
                .username("reader")
                .email("reader@example.com")
                .password("encoded")
                .uiFontId(8L)
                .readerFontId(9L)
                .build();
        when(repository.findByUsername("reader")).thenReturn(Optional.of(user));
        when(repository.save(user)).thenReturn(user);
        when(fonts.findByIdAndEnabledTrue(8L)).thenReturn(Optional.of(
                FontAsset.builder().id(8L).enabled(true).build()));
        UserService service = new UserService(repository, fonts);
        ObjectMapper mapper = new ObjectMapper();

        service.updatePreferences(
                "reader", mapper.readValue("{\"theme\":\"modern\"}",
                        UserPreferencesDTO.class));
        assertEquals(8L, user.getUiFontId());
        assertEquals(9L, user.getReaderFontId());

        service.updatePreferences(
                "reader", mapper.readValue("{\"readerFontId\":null}",
                        UserPreferencesDTO.class));
        assertEquals(null, user.getReaderFontId());
    }

    @Test
    void rejectsMissingOrDisabledFontPreference() {
        UserRepository repository = mock(UserRepository.class);
        FontAssetRepository fonts = mock(FontAssetRepository.class);
        User user = User.builder()
                .username("reader")
                .email("reader@example.com")
                .password("encoded")
                .build();
        when(repository.findByUsername("reader")).thenReturn(Optional.of(user));
        when(fonts.findByIdAndEnabledTrue(99L)).thenReturn(Optional.empty());
        UserService service = new UserService(repository, fonts);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.updatePreferences(
                        "reader",
                        UserPreferencesDTO.builder().uiFontId(99L).build()));
    }
}
