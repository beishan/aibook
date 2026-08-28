package com.aibook.service;

import com.aibook.dto.UserPreferencesDTO;
import com.aibook.model.entity.User;
import com.aibook.model.entity.UserPreference;
import com.aibook.model.entity.FontAsset;
import com.aibook.repository.FontAssetRepository;
import com.aibook.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.Map;

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
                .preferences(UserPreference.builder()
                        .webTheme("warm")
                        .libraryViewMode("card")
                        .build())
                .build();
        when(repository.findByUsername("reader")).thenReturn(Optional.of(user));
        when(repository.save(user)).thenReturn(user);
        UserService service = new UserService(repository);

        UserPreferencesDTO result = service.updatePreferences(
                "reader",
                UserPreferencesDTO.builder()
                        .libraryViewMode("compact-card")
                        .libraryPageSize(30)
                        .build());

        assertEquals("warm", result.getTheme());
        assertEquals("compact-card", result.getLibraryViewMode());
        assertEquals(30, result.getLibraryPageSize());
        assertEquals(30, result.getLibraryCardPageSize());
        assertEquals(30, result.getLibraryListPageSize());
        assertEquals(2, result.getScanThreadCount());
        assertEquals(58, result.getDockSize());
        assertEquals(72, result.getDockOpacity());
        assertEquals(128, result.getDockMagnification());
        assertEquals(24, result.getDockBlur());
        assertEquals("minimal", result.getDockIconStyle());
        assertEquals("#2563EB", result.getModernThemeColor());
        assertEquals("#A0522D", result.getWarmThemeColor());
        assertEquals("#2E7D5A", result.getNaturalThemeColor());
        assertEquals("#007AFF", result.getMacos26ThemeColor());
        assertEquals("gradient", result.getThemeBackgrounds().get("macos26").getMode());
        assertEquals("gradient", result.getThemeBackgrounds().get("natural").getMode());
    }

    @Test
    void updatesAndNormalizesThemeColors() {
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
                        .theme("macos26")
                        .modernThemeColor("#0891b2")
                        .warmThemeColor("#b7791f")
                        .naturalThemeColor("#3c8d78")
                        .macos26ThemeColor("#5856d6")
                        .build());

        assertEquals("macos26", result.getTheme());
        assertEquals("#0891B2", result.getModernThemeColor());
        assertEquals("#B7791F", result.getWarmThemeColor());
        assertEquals("#3C8D78", result.getNaturalThemeColor());
        assertEquals("#5856D6", result.getMacos26ThemeColor());
    }

    @Test
    void rejectsInvalidThemeColors() {
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
                        UserPreferencesDTO.builder().modernThemeColor("blue").build()));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.updatePreferences(
                        "reader",
                        UserPreferencesDTO.builder().warmThemeColor("#FFF").build()));
    }

    @Test
    void updatesThemeBackgroundSettings() {
        UserRepository repository = mock(UserRepository.class);
        User user = User.builder()
                .username("reader")
                .email("reader@example.com")
                .password("encoded")
                .build();
        when(repository.findByUsername("reader")).thenReturn(Optional.of(user));
        when(repository.save(user)).thenReturn(user);
        UserService service = new UserService(repository);
        Map<String, UserPreferencesDTO.ThemeBackgroundDTO> backgrounds = validBackgrounds();
        backgrounds.get("modern").setPageColor("#eaf0f7");

        UserPreferencesDTO result = service.updatePreferences(
                "reader",
                UserPreferencesDTO.builder().themeBackgrounds(backgrounds).build());

        assertEquals("#EAF0F7", result.getThemeBackgrounds().get("modern").getPageColor());
        assertEquals(72, result.getThemeBackgrounds().get("natural").getSurfaceOpacity());
    }

    @Test
    void rejectsInvalidThemeBackgroundSettings() {
        UserRepository repository = mock(UserRepository.class);
        User user = User.builder()
                .username("reader")
                .email("reader@example.com")
                .password("encoded")
                .build();
        when(repository.findByUsername("reader")).thenReturn(Optional.of(user));
        UserService service = new UserService(repository);
        Map<String, UserPreferencesDTO.ThemeBackgroundDTO> incomplete = validBackgrounds();
        incomplete.remove("warm");
        Map<String, UserPreferencesDTO.ThemeBackgroundDTO> invalidOpacity = validBackgrounds();
        invalidOpacity.get("natural").setSurfaceOpacity(10);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.updatePreferences(
                        "reader",
                        UserPreferencesDTO.builder().themeBackgrounds(incomplete).build()));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.updatePreferences(
                        "reader",
                        UserPreferencesDTO.builder().themeBackgrounds(invalidOpacity).build()));
    }

    private Map<String, UserPreferencesDTO.ThemeBackgroundDTO> validBackgrounds() {
        Map<String, UserPreferencesDTO.ThemeBackgroundDTO> values = new LinkedHashMap<>();
        values.put("modern", new UserPreferencesDTO.ThemeBackgroundDTO(
                "solid", "#F5F5F5", "#EEF2F7", "#FFFFFF", 100, "#FFFFFF", 100));
        values.put("warm", new UserPreferencesDTO.ThemeBackgroundDTO(
                "solid", "#FAF6F1", "#F3E9DC", "#FFFBF5", 100, "#FFFBF5", 100));
        values.put("natural", new UserPreferencesDTO.ThemeBackgroundDTO(
                "gradient", "#E8F5E9", "#E0F2F1", "#FFFFFF", 75, "#FFFFFF", 72));
        values.put("macos26", new UserPreferencesDTO.ThemeBackgroundDTO(
                "gradient", "#DCEBFA", "#F1E4F8", "#F8FBFF", 62, "#FFFFFF", 58));
        return values;
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
                        .dockIconStyle("custom")
                        .build());

        assertEquals(64, result.getDockSize());
        assertEquals(66, result.getDockOpacity());
        assertEquals(136, result.getDockMagnification());
        assertEquals(30, result.getDockBlur());
        assertEquals("custom", result.getDockIconStyle());
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
        assertThrows(
                IllegalArgumentException.class,
                () -> service.updatePreferences(
                        "reader",
                        UserPreferencesDTO.builder().dockIconStyle("pixel").build()));
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
        assertThrows(
                IllegalArgumentException.class,
                () -> service.updatePreferences(
                        "reader",
                        UserPreferencesDTO.builder().libraryListPageSize(25).build()));
    }

    @Test
    void normalizesLegacyLibraryPageSizesToNewDefault() {
        UserRepository repository = mock(UserRepository.class);
        User user = User.builder()
                .username("reader")
                .email("reader@example.com")
                .password("encoded")
                .preferences(UserPreference.builder()
                        .libraryPageSize(18)
                        .libraryListPageSize(60)
                        .build())
                .build();
        when(repository.findByUsername("reader")).thenReturn(Optional.of(user));
        UserService service = new UserService(repository);

        UserPreferencesDTO result = service.getPreferences("reader");

        assertEquals(10, result.getLibraryCardPageSize());
        assertEquals(10, result.getLibraryListPageSize());
    }

    @Test
    void storesCardAndListPageSizesIndependently() {
        UserRepository repository = mock(UserRepository.class);
        User user = User.builder()
                .username("reader")
                .email("reader@example.com")
                .password("encoded")
                .preferences(UserPreference.builder()
                        .libraryPageSize(10)
                        .libraryListPageSize(10)
                        .build())
                .build();
        when(repository.findByUsername("reader")).thenReturn(Optional.of(user));
        when(repository.save(user)).thenReturn(user);
        UserService service = new UserService(repository);

        UserPreferencesDTO result = service.updatePreferences(
                "reader",
                UserPreferencesDTO.builder()
                        .libraryCardPageSize(50)
                        .libraryListPageSize(200)
                        .build());

        assertEquals(50, user.getLibraryPageSize());
        assertEquals(200, user.getLibraryListPageSize());
        assertEquals(50, result.getLibraryCardPageSize());
        assertEquals(200, result.getLibraryListPageSize());
    }

    @Test
    void distinguishesOmittedFontFromExplicitNull() throws Exception {
        UserRepository repository = mock(UserRepository.class);
        FontAssetRepository fonts = mock(FontAssetRepository.class);
        User user = User.builder()
                .username("reader")
                .email("reader@example.com")
                .password("encoded")
                .preferences(UserPreference.builder()
                        .uiFontId(8L)
                        .readerFontId(9L)
                        .build())
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
