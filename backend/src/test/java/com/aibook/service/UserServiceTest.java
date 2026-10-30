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
    void bookDisplaySettingsTabPersistsAndIsolatesAccounts() {
        UserRepository repository = mock(UserRepository.class);
        User reader = User.builder().username("reader").build();
        User other = User.builder().username("other").build();
        when(repository.findByUsername("reader")).thenReturn(Optional.of(reader));
        when(repository.findByUsername("other")).thenReturn(Optional.of(other));
        when(repository.save(reader)).thenReturn(reader);
        UserService service = new UserService(repository);
        assertEquals("name", service.getPreferences("reader").getBookDisplaySettingsTab());
        for (String tab : new String[] {"name", "cover", "badges", "placeholder"}) {
            var result = service.updatePreferences("reader", UserPreferencesDTO.builder()
                    .bookDisplaySettingsTab(tab).build());
            assertEquals(tab, result.getBookDisplaySettingsTab());
        }
        service.updatePreferences("reader", UserPreferencesDTO.builder().hideBookNames(true).build());
        assertEquals("placeholder", service.getPreferences("reader").getBookDisplaySettingsTab());
        assertEquals("name", service.getPreferences("other").getBookDisplaySettingsTab());
        assertThrows(IllegalArgumentException.class, () -> service.updatePreferences("reader",
                UserPreferencesDTO.builder().bookDisplaySettingsTab("invalid").build()));
        assertEquals("placeholder", reader.getBookDisplaySettingsTab());
    }

    @Test
    void bookNameReplacementCharacterPersistsValidUnicodeAndRejectsInvisibleOrMultipleCharacters() {
        UserRepository repository = mock(UserRepository.class);
        User reader = User.builder().username("reader").build();
        User other = User.builder().username("other").build();
        when(repository.findByUsername("reader")).thenReturn(Optional.of(reader));
        when(repository.findByUsername("other")).thenReturn(Optional.of(other));
        when(repository.save(reader)).thenReturn(reader);
        UserService service = new UserService(repository);
        assertEquals("*", service.getPreferences("reader").getBookNameMaskCharacter());
        for (String character : new String[] {"#", "●", "隐", "🙂", "👨‍👩‍👧", "🇨🇳", "e\u0301", "<"}) {
            var result = service.updatePreferences("reader", UserPreferencesDTO.builder()
                    .bookNameMaskCharacter(character).build());
            assertEquals(character, result.getBookNameMaskCharacter());
            assertEquals(character, reader.getPreferences().getBookNameMaskCharacter());
        }
        service.updatePreferences("reader", UserPreferencesDTO.builder().hideBookNames(true).build());
        assertEquals("<", service.getPreferences("reader").getBookNameMaskCharacter());
        assertEquals("*", service.getPreferences("other").getBookNameMaskCharacter());
        for (String invalid : new String[] {"", " ", "\n", "\t", "\u200b", "\u0301", "**", "🙂🙂", "*".repeat(65)}) {
            assertThrows(IllegalArgumentException.class, () -> service.updatePreferences("reader",
                    UserPreferencesDTO.builder().bookNameMaskCharacter(invalid).build()));
        }
        reader.setBookNameMaskCharacter("invalid");
        assertEquals("*", service.getPreferences("reader").getBookNameMaskCharacter());
        assertEquals("*", service.updatePreferences("reader", UserPreferencesDTO.builder()
                .bookNameMaskCharacter("*").build()).getBookNameMaskCharacter());
    }


    @Test
    void bookNameDisplayDefaultsPersistAndDoNotCrossAccounts() {
        UserRepository repository = mock(UserRepository.class);
        User reader = User.builder().username("reader").build();
        User other = User.builder().username("other").build();
        when(repository.findByUsername("reader")).thenReturn(Optional.of(reader));
        when(repository.findByUsername("other")).thenReturn(Optional.of(other));
        when(repository.save(reader)).thenReturn(reader);
        UserService service = new UserService(repository);
        var defaults = service.getPreferences("reader");
        assertEquals(false, defaults.getHideBookNames());
        assertEquals("HEAD_TAIL", defaults.getBookNameMaskMode());
        assertEquals(2, defaults.getBookNamePrefixLength());
        var saved = service.updatePreferences("reader", UserPreferencesDTO.builder()
                .hideBookNames(true).bookNameMaskMode("PREFIX").bookNamePrefixLength(3).build());
        assertEquals(true, saved.getHideBookNames());
        assertEquals("PREFIX", saved.getBookNameMaskMode());
        assertEquals(3, saved.getBookNamePrefixLength());
        assertEquals(3, reader.getPreferences().getBookNamePrefixLength());
        service.updatePreferences("reader", UserPreferencesDTO.builder().shelfPageSize(48).build());
        assertEquals(true, service.getPreferences("reader").getHideBookNames());
        assertEquals(false, service.getPreferences("other").getHideBookNames());
        for (int invalid : new int[] {-1, 0, 101}) {
            assertThrows(IllegalArgumentException.class, () -> service.updatePreferences("reader",
                    UserPreferencesDTO.builder().bookNamePrefixLength(invalid).build()));
        }
        assertThrows(IllegalArgumentException.class, () -> service.updatePreferences("reader",
                UserPreferencesDTO.builder().bookNameMaskMode("UNKNOWN").build()));
        for (int length : new int[] {1, 100}) {
            assertEquals(length, service.updatePreferences("reader",
                    UserPreferencesDTO.builder().bookNamePrefixLength(length).build()).getBookNamePrefixLength());
        }
        service.updatePreferences("reader", UserPreferencesDTO.builder()
                .hideBookNames(false).bookNameMaskMode("HEAD_TAIL").build());
        assertEquals(false, service.getPreferences("reader").getHideBookNames());
        assertEquals("HEAD_TAIL", service.getPreferences("reader").getBookNameMaskMode());
        reader.setBookNamePrefixLength(101);
        reader.setBookNameMaskMode("INVALID");
        assertEquals(2, service.getPreferences("reader").getBookNamePrefixLength());
        assertEquals("HEAD_TAIL", service.getPreferences("reader").getBookNameMaskMode());
    }


    @Test
    void shelfPageSizeDefaultsPersistsAndKeepsAccountsSeparate() {
        UserRepository repository = mock(UserRepository.class);
        User reader = User.builder().username("reader").build();
        User other = User.builder().username("other").build();
        when(repository.findByUsername("reader")).thenReturn(Optional.of(reader));
        when(repository.findByUsername("other")).thenReturn(Optional.of(other));
        when(repository.save(reader)).thenReturn(reader);
        UserService service = new UserService(repository);
        assertEquals(24, service.getPreferences("reader").getShelfPageSize());
        for (int size : new int[] {12, 24, 48, 96}) {
            assertEquals(size, service.updatePreferences("reader",
                    UserPreferencesDTO.builder().shelfPageSize(size).build()).getShelfPageSize());
            assertEquals(size, reader.getPreferences().getShelfPageSize());
        }
        service.updatePreferences("reader", UserPreferencesDTO.builder().libraryViewMode("list").build());
        assertEquals(96, service.getPreferences("reader").getShelfPageSize());
        assertEquals(24, service.getPreferences("other").getShelfPageSize());
        for (int invalid : new int[] {-1, 0, 25, 100, 1000}) {
            assertThrows(IllegalArgumentException.class, () -> service.updatePreferences("reader",
                    UserPreferencesDTO.builder().shelfPageSize(invalid).build()));
        }
        assertEquals(96, reader.getShelfPageSize());
        reader.setShelfPageSize(25);
        assertEquals(24, service.getPreferences("reader").getShelfPageSize());
    }


    @Test
    void persistsTrialViewModeAndPreservesItOnUnrelatedPartialUpdate() {
        UserRepository repository = mock(UserRepository.class);
        User user = User.builder().username("reader").build();
        when(repository.findByUsername("reader")).thenReturn(Optional.of(user));
        when(repository.save(user)).thenReturn(user);
        UserService service = new UserService(repository);
        var result = service.updatePreferences("reader",
                UserPreferencesDTO.builder().scraperTrialViewMode("card").build());
        assertEquals("card", user.getPreferences().getScraperTrialViewMode());
        assertEquals("card", result.getScraperTrialViewMode());
        service.updatePreferences("reader", UserPreferencesDTO.builder().libraryViewMode("list").build());
        assertEquals("card", service.getPreferences("reader").getScraperTrialViewMode());
        assertThrows(IllegalArgumentException.class, () -> service.updatePreferences("reader",
                UserPreferencesDTO.builder().scraperTrialViewMode("grid").build()));
        assertEquals("card", user.getScraperTrialViewMode());
        service.updatePreferences("reader", UserPreferencesDTO.builder().scraperTrialViewMode("table").build());
        assertEquals("table", service.getPreferences("reader").getScraperTrialViewMode());
    }

    @Test
    void persistsStatisticsWindowAndRejectsInvalidCombinations() {
        UserRepository repository = mock(UserRepository.class);
        User user = User.builder().username("reader").build();
        when(repository.findByUsername("reader")).thenReturn(Optional.of(user));
        when(repository.save(user)).thenReturn(user);
        UserService service = new UserService(repository);
        UserPreferencesDTO result = service.updatePreferences("reader",
                UserPreferencesDTO.builder().crawlerStatisticsWindow("minute:60").build());
        assertEquals("minute:60", result.getCrawlerStatisticsWindow());
        assertEquals("minute:60", service.getPreferences("reader").getCrawlerStatisticsWindow());
        assertThrows(IllegalArgumentException.class, () -> service.updatePreferences("reader",
                UserPreferencesDTO.builder().crawlerStatisticsWindow("hour:90").build()));
        assertEquals("minute:60", user.getCrawlerStatisticsWindow());
    }

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
                        .authorPageSize(20)
                        .build());

        assertEquals("warm", result.getTheme());
        assertEquals("compact-card", result.getLibraryViewMode());
        assertEquals(30, result.getLibraryPageSize());
        assertEquals(30, result.getLibraryCardPageSize());
        assertEquals(30, result.getLibraryListPageSize());
        assertEquals(20, result.getAuthorPageSize());
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
    void storesCrawlerChapterPreferencesForTheAccount() {
        UserRepository repository = mock(UserRepository.class);
        User user = User.builder()
                .username("reader")
                .email("reader@example.com")
                .password("encoded")
                .preferences(UserPreference.builder().build())
                .build();
        when(repository.findByUsername("reader")).thenReturn(Optional.of(user));
        when(repository.save(user)).thenReturn(user);
        UserService service = new UserService(repository);

        UserPreferencesDTO result = service.updatePreferences(
                "reader",
                UserPreferencesDTO.builder()
                        .crawlerPollingIntervalSeconds(10)
                        .crawlerFollowCurrentChapter(true)
                        .crawlerChapterPageSize(50)
                        .crawlerDiscoveryViewMode("card")
                        .crawlerBookViewMode("card")
                        .crawlerHideBookNames(true)
                        .build());

        assertEquals(10, user.getCrawlerPollingIntervalSeconds());
        assertEquals(true, user.getCrawlerFollowCurrentChapter());
        assertEquals(50, user.getCrawlerChapterPageSize());
        assertEquals("card", user.getCrawlerDiscoveryViewMode());
        assertEquals("card", user.getCrawlerBookViewMode());
        assertEquals(10, result.getCrawlerPollingIntervalSeconds());
        assertEquals(true, result.getCrawlerFollowCurrentChapter());
        assertEquals(50, result.getCrawlerChapterPageSize());
        assertEquals("card", result.getCrawlerDiscoveryViewMode());
        assertEquals("card", result.getCrawlerBookViewMode());
        assertEquals(true, user.getCrawlerHideBookNames());
        assertEquals(true, result.getCrawlerHideBookNames());
        assertEquals(true, service.getPreferences("reader").getCrawlerHideBookNames());

        service.updatePreferences("reader", UserPreferencesDTO.builder().crawlerFollowCurrentChapter(false).build());
        assertEquals(true, user.getCrawlerHideBookNames());
        UserPreferencesDTO visible = service.updatePreferences(
                "reader", UserPreferencesDTO.builder().crawlerHideBookNames(false).build());
        assertEquals(false, user.getCrawlerHideBookNames());
        assertEquals(false, visible.getCrawlerHideBookNames());
    }

    @Test
    void rejectsInvalidCrawlerChapterPageSize() {
        UserRepository repository = mock(UserRepository.class);
        User user = User.builder()
                .username("reader")
                .email("reader@example.com")
                .password("encoded")
                .build();
        when(repository.findByUsername("reader")).thenReturn(Optional.of(user));
        UserService service = new UserService(repository);

        assertThrows(IllegalArgumentException.class, () -> service.updatePreferences(
                "reader", UserPreferencesDTO.builder().crawlerChapterPageSize(30).build()));
    }

    @Test
    void rejectsInvalidCrawlerPollingInterval() {
        UserRepository repository = mock(UserRepository.class);
        User user = User.builder()
                .username("reader")
                .email("reader@example.com")
                .password("encoded")
                .build();
        when(repository.findByUsername("reader")).thenReturn(Optional.of(user));
        UserService service = new UserService(repository);

        assertThrows(IllegalArgumentException.class, () -> service.updatePreferences(
                "reader", UserPreferencesDTO.builder().crawlerPollingIntervalSeconds(2).build()));
    }

    @Test
    void rejectsInvalidCrawlerDiscoveryViewMode() {
        UserRepository repository = mock(UserRepository.class);
        User user = User.builder()
                .username("reader")
                .email("reader@example.com")
                .password("encoded")
                .build();
        when(repository.findByUsername("reader")).thenReturn(Optional.of(user));
        UserService service = new UserService(repository);

        assertThrows(IllegalArgumentException.class, () -> service.updatePreferences(
                "reader", UserPreferencesDTO.builder().crawlerDiscoveryViewMode("grid").build()));
    }

    @Test
    void rejectsInvalidCrawlerBookViewMode() {
        UserRepository repository = mock(UserRepository.class);
        User user = User.builder()
                .username("reader")
                .email("reader@example.com")
                .password("encoded")
                .build();
        when(repository.findByUsername("reader")).thenReturn(Optional.of(user));
        UserService service = new UserService(repository);

        assertThrows(IllegalArgumentException.class, () -> service.updatePreferences(
                "reader", UserPreferencesDTO.builder().crawlerBookViewMode("grid").build()));
    }

    @Test
    void storesAndRestoresReaderSettingsForTheAccount() throws Exception {
        UserRepository repository = mock(UserRepository.class);
        User user = User.builder()
                .username("reader")
                .email("reader@example.com")
                .password("encoded")
                .preferences(UserPreference.builder().build())
                .build();
        when(repository.findByUsername("reader")).thenReturn(Optional.of(user));
        when(repository.save(user)).thenReturn(user);
        UserService service = new UserService(repository);

        UserPreferencesDTO result = service.updatePreferences(
                "reader",
                UserPreferencesDTO.builder().readerSettings(validReaderSettings()).build());

        UserPreferencesDTO.ReaderSettingsDTO stored = new ObjectMapper().readValue(
                user.getReaderSettings(), UserPreferencesDTO.ReaderSettingsDTO.class);
        assertEquals("readingRoom", stored.getAppearance());
        assertEquals("readium", stored.getEpubEngine());
        assertEquals("managed:12", stored.getFontFamily());
        assertEquals(22, stored.getFontSize());
        assertEquals(2.1, stored.getLineHeight());
        assertEquals(24, stored.getParagraphSpacing());
        assertEquals("wide", stored.getContentWidth());
        assertEquals("#f5f5dc", stored.getBackgroundColor());
        assertEquals("custom-7", stored.getBackgroundImageId());
        assertEquals(true, stored.getPaginationMode());
        assertEquals("double", stored.getScreenMode());
        assertEquals(false, stored.getTextIndent());
        assertEquals(false, stored.getShowProgress());
        assertEquals("readingRoom", result.getReaderSettings().getAppearance());
        assertEquals("double", service.getPreferences("reader").getReaderSettings().getScreenMode());
    }

    @Test
    void rejectsInvalidReaderSettings() {
        UserRepository repository = mock(UserRepository.class);
        User user = User.builder()
                .username("reader")
                .email("reader@example.com")
                .password("encoded")
                .build();
        when(repository.findByUsername("reader")).thenReturn(Optional.of(user));
        UserService service = new UserService(repository);
        UserPreferencesDTO.ReaderSettingsDTO invalidSize = validReaderSettings();
        invalidSize.setFontSize(48);
        UserPreferencesDTO.ReaderSettingsDTO invalidBackground = validReaderSettings();
        invalidBackground.setBackgroundImageId("javascript:alert(1)");

        assertThrows(IllegalArgumentException.class, () -> service.updatePreferences(
                "reader", UserPreferencesDTO.builder().readerSettings(invalidSize).build()));
        assertThrows(IllegalArgumentException.class, () -> service.updatePreferences(
                "reader", UserPreferencesDTO.builder().readerSettings(invalidBackground).build()));
    }

    @Test
    void blueGrayAppearancePersistsItsTypographyWithoutChangingLegacyValues() throws Exception {
        UserRepository repository = mock(UserRepository.class);
        User user = User.builder().username("reader").preferences(UserPreference.builder().build()).build();
        when(repository.findByUsername("reader")).thenReturn(Optional.of(user));
        when(repository.save(user)).thenReturn(user);
        UserService service = new UserService(repository);
        UserPreferencesDTO.ReaderSettingsDTO settings = validReaderSettings();
        settings.setAppearance("blueGray");
        settings.setBlueGray(new UserPreferencesDTO.ReaderTypographyDTO(
                "default", 24, 1.9, 32, "wide", "auto", "none"));

        service.updatePreferences("reader", UserPreferencesDTO.builder().readerSettings(settings).build());
        UserPreferencesDTO.ReaderSettingsDTO restored = service.getPreferences("reader").getReaderSettings();
        assertEquals("blueGray", restored.getAppearance());
        assertEquals(24, restored.getBlueGray().fontSize());
        assertEquals("default", restored.getBlueGray().fontFamily());
        assertEquals(22, restored.getFontSize());
        assertEquals("managed:12", restored.getFontFamily());
        assertEquals("#f5f5dc", restored.getBackgroundColor());

        restored.setAppearance("classic");
        service.updatePreferences("reader", UserPreferencesDTO.builder().readerSettings(restored).build());
        assertEquals(24, service.getPreferences("reader").getReaderSettings().getBlueGray().fontSize());
        UserPreferencesDTO.ReaderSettingsDTO serialized = new ObjectMapper().readValue(
                user.getReaderSettings(), UserPreferencesDTO.ReaderSettingsDTO.class);
        assertEquals("classic", serialized.getAppearance());
        assertEquals(22, serialized.getFontSize());
    }

    @Test
    void rejectsInvalidBlueGrayTypographyWithoutSavingIt() {
        UserRepository repository = mock(UserRepository.class);
        User user = User.builder().username("reader").build();
        when(repository.findByUsername("reader")).thenReturn(Optional.of(user));
        UserService service = new UserService(repository);
        UserPreferencesDTO.ReaderSettingsDTO settings = validReaderSettings();
        settings.setBlueGray(new UserPreferencesDTO.ReaderTypographyDTO(
                "default", 48, 1.9, 32, "wide", "auto", "none"));
        assertThrows(IllegalArgumentException.class, () -> service.updatePreferences(
                "reader", UserPreferencesDTO.builder().readerSettings(settings).build()));
        settings.setBlueGray(new UserPreferencesDTO.ReaderTypographyDTO(
                "default", 22, 1.9, 32, "wide", "auto", "javascript:alert(1)"));
        assertThrows(IllegalArgumentException.class, () -> service.updatePreferences(
                "reader", UserPreferencesDTO.builder().readerSettings(settings).build()));
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).save(user);
    }

    @Test
    void warmPaperTypographyRemainsIndependentOfBlueGrayAndLegacyTypography() {
        UserRepository repository = mock(UserRepository.class);
        User user = User.builder().username("reader").preferences(UserPreference.builder().build()).build();
        when(repository.findByUsername("reader")).thenReturn(Optional.of(user));
        when(repository.save(user)).thenReturn(user);
        UserService service = new UserService(repository);
        UserPreferencesDTO.ReaderSettingsDTO settings = validReaderSettings();
        settings.setAppearance("warmPaper");
        settings.setBlueGray(new UserPreferencesDTO.ReaderTypographyDTO(
                "default", 23, 1.8, 20, "wide", "auto", "none"));
        settings.setWarmPaper(new UserPreferencesDTO.ReaderTypographyDTO(
                "default", 25, 1.9, 30, "wide", "auto", "none", "center"));

        service.updatePreferences("reader", UserPreferencesDTO.builder().readerSettings(settings).build());
        UserPreferencesDTO.ReaderSettingsDTO restored = service.getPreferences("reader").getReaderSettings();
        assertEquals("warmPaper", restored.getAppearance());
        assertEquals(25, restored.getWarmPaper().fontSize());
        assertEquals("center", restored.getWarmPaper().textAlign());
        assertEquals(23, restored.getBlueGray().fontSize());
        assertEquals(22, restored.getFontSize());
        restored.setAppearance("blueGray");
        service.updatePreferences("reader", UserPreferencesDTO.builder().readerSettings(restored).build());
        assertEquals(25, service.getPreferences("reader").getReaderSettings().getWarmPaper().fontSize());
    }

    @Test
    void rejectsInvalidWarmPaperAlignmentAndBackground() {
        UserRepository repository = mock(UserRepository.class);
        User user = User.builder().username("reader").build();
        when(repository.findByUsername("reader")).thenReturn(Optional.of(user));
        UserService service = new UserService(repository);
        UserPreferencesDTO.ReaderSettingsDTO settings = validReaderSettings();
        settings.setAppearance("warmPaper");
        settings.setWarmPaper(new UserPreferencesDTO.ReaderTypographyDTO(
                "default", 24, 1.9, 30, "wide", "auto", "none", "invalid"));
        assertThrows(IllegalArgumentException.class, () -> service.updatePreferences(
                "reader", UserPreferencesDTO.builder().readerSettings(settings).build()));
        settings.setWarmPaper(new UserPreferencesDTO.ReaderTypographyDTO(
                "default", 24, 1.9, 30, "wide", "auto", "javascript:alert(1)", "justify"));
        assertThrows(IllegalArgumentException.class, () -> service.updatePreferences(
                "reader", UserPreferencesDTO.builder().readerSettings(settings).build()));
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).save(user);
    }

    private UserPreferencesDTO.ReaderSettingsDTO validReaderSettings() {
        return new UserPreferencesDTO.ReaderSettingsDTO(
                "readingRoom",
                "readium",
                "managed:12",
                22,
                2.1,
                24,
                "wide",
                "#f5f5dc",
                "custom-7",
                true,
                "double",
                false,
                false);
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
    @Test
    void upgradesLegacyNavigationWithoutLosingCustomizations() throws Exception {
        UserRepository repository = mock(UserRepository.class);
        User user = User.builder().username("reader").build();
        when(repository.findByUsername("reader")).thenReturn(Optional.of(user));
        when(repository.save(user)).thenReturn(user);
        var service = new UserService(repository);
        var legacy = new java.util.ArrayList<>(service.getPreferences("reader").getDockNavigationItems());
        legacy.removeIf(item -> "recommendations".equals(item.getKey()) || "libraries".equals(item.getKey()));
        legacy.get(0).setTitle("我的定制首页");
        legacy.get(0).setEnabled(false);
        user.setDockNavigationSettings(new ObjectMapper().writeValueAsString(legacy));
        var restored = service.getPreferences("reader").getDockNavigationItems();
        assertEquals(11, restored.size());
        assertEquals("我的定制首页", restored.get(0).getTitle());
        assertEquals(false, restored.get(0).getEnabled());
        assertEquals("recommendations", restored.get(9).getKey());
        assertEquals("libraries", restored.get(10).getKey());
        var saved = service.updatePreferences("reader",
                UserPreferencesDTO.builder().dockNavigationItems(legacy).build());
        assertEquals(11, saved.getDockNavigationItems().size());
    }

}
