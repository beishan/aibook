package com.aibook.library;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aibook.model.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class LibraryServiceTest {
    @Test
    void rejectsUnknownAndOversizedConfigurationInsteadOfSavingExecutableContent() {
        var service = new LibraryService(mock(NamedParameterJdbcTemplate.class), new ObjectMapper());
        assertThrows(ResponseStatusException.class, () -> service.encode(Map.of("accent", "url(https://other)")));
        assertThrows(ResponseStatusException.class, () -> service.encode(Map.of("modules", List.of("recent", "recent"))));
        assertThrows(ResponseStatusException.class, () -> service.encode(Map.of("pageSize", 100000)));
        assertThrows(ResponseStatusException.class, () -> service.encode(Map.of("showCover", "true")));
        assertDoesNotThrow(() -> service.encode(Map.of("modules", List.of())));
    }

    @Test
    void validatesNamesPaginationAndLiteralSearch() {
        assertEquals("文学馆", LibraryService.name(" 文学馆 "));
        assertThrows(ResponseStatusException.class, () -> LibraryService.name("  "));
        assertThrows(ResponseStatusException.class, () -> LibraryService.checkPage(-1, 24));
        assertThrows(ResponseStatusException.class, () -> LibraryService.checkIds(List.of(0L), 20));
        assertEquals("%100!%书!_!!%", LibraryService.search("100%书_!"));
    }

    @Test
    void inaccessibleLibraryCannotReachBookOrDeleteQueries() {
        var jdbc = mock(NamedParameterJdbcTemplate.class);
        var service = new LibraryService(jdbc, new ObjectMapper());
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of());
        assertThrows(ResponseStatusException.class, () -> service.delete(User.builder().id(2L).build(), 1));
        verify(jdbc, never()).update(anyString(), any(MapSqlParameterSource.class));
    }
}
