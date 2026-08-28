package com.aibook.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aibook.dto.CoverPrivacyScopeDTO;
import com.aibook.model.entity.User;
import com.aibook.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CoverPrivacyServiceTest {

    private UserRepository userRepository;
    private CoverPrivacyService service;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        service = new CoverPrivacyService(userRepository, new ObjectMapper());
        user = User.builder().id(7L).username("reader").build();
        when(userRepository.save(user)).thenReturn(user);
    }

    @Test
    void reportsNewScopesAsUninitialized() {
        CoverPrivacyScopeDTO books = service.getBookCoverSettings(user);
        CoverPrivacyScopeDTO randomCovers = service.getRandomCoverSettings(user);

        assertFalse(books.initialized());
        assertFalse(randomCovers.initialized());
        assertFalse(books.allHidden());
        assertTrue(books.overrides().isEmpty());
    }

    @Test
    void persistsAndNormalizesBookCoverOverrides() {
        Map<Long, Boolean> overrides = new LinkedHashMap<>();
        overrides.put(12L, true);
        overrides.put(-1L, false);
        overrides.put(19L, false);

        CoverPrivacyScopeDTO result = service.updateBookCoverSettings(
                user, new CoverPrivacyScopeDTO(true, true, overrides));

        assertTrue(result.initialized());
        assertTrue(result.allHidden());
        assertEquals(Map.of(12L, true, 19L, false), result.overrides());
        assertEquals("{\"12\":true,\"19\":false}", user.getBookCoverVisibilityOverrides());
    }

    @Test
    void invalidStoredJsonFallsBackToEmptyOverrides() {
        user.setAllRandomCoversHidden(false);
        user.setRandomCoverVisibilityOverrides("not-json");

        CoverPrivacyScopeDTO result = service.getRandomCoverSettings(user);

        assertTrue(result.initialized());
        assertFalse(result.allHidden());
        assertTrue(result.overrides().isEmpty());
    }
}
