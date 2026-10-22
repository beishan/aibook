package com.aibook.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aibook.model.entity.User;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.*;

class HomeServiceTest {
    @Test
    void everySectionUsesOwnedVisibleBooksAndBoundedLists() {
        var jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForMap(anyString(), any(SqlParameterSource.class))).thenReturn(Map.of("totalBooks", 3L));
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(List.of());
        var service = new HomeService(jdbc);
        var result = service.dashboard(User.builder().id(42L).build());
        assertThat(result.overview().get("totalBooks")).isEqualTo(3L);
        var sql = ArgumentCaptor.forClass(String.class);
        var args = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).queryForMap(sql.capture(), args.capture());
        verify(jdbc, times(6)).queryForList(sql.capture(), args.capture());
        assertThat(sql.getAllValues()).allSatisfy(query -> assertThat(query)
                .contains("b.user_id = :user", "b.deleted_at IS NULL", "d.library_visible = true"));
        assertThat(args.getAllValues()).allSatisfy(value -> assertThat(value.getValue("user")).isEqualTo(42L));
        assertThat(sql.getAllValues()).anySatisfy(query -> assertThat(query).contains(
                "p.user_id=:user", "p.last_read_at IS NOT NULL", "b.reading_status <> 'FINISHED'",
                "ORDER BY p.last_read_at DESC", "LIMIT 3"));
        assertThat(sql.getAllValues()).anySatisfy(query -> assertThat(query)
                .contains("b.is_wanted=true", "LIMIT 4"));
        assertThat(sql.getAllValues()).anySatisfy(query -> assertThat(query)
                .contains("b.updated_at > b.created_at", "ORDER BY b.updated_at DESC", "LIMIT 5"));
        assertThat(sql.getAllValues()).anySatisfy(query -> assertThat(query)
                .contains("c.user_id=:user", "GROUP BY c.id, c.name", "LIMIT 8"));
        assertThat(sql.getAllValues()).filteredOn(query -> query.startsWith("SELECT b.id"))
                .allSatisfy(query -> assertThat(query).contains("b.cover_hidden AS \"coverHidden\""));
    }

    @Test
    void timestampsRemainLocalDateTimeStringsAndEmptySectionsRemainEmpty() {
        var jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForMap(anyString(), any(SqlParameterSource.class))).thenReturn(Map.of("totalBooks", 0L));
        var timestamp = LocalDateTime.of(2026, 10, 22, 8, 30);
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (!sql.contains("ORDER BY b.created_at DESC") || sql.contains("is_wanted")) return List.of();
            var row = new LinkedHashMap<String, Object>();
            row.put("id", 7L);
            row.put("createdAt", Timestamp.valueOf(timestamp));
            row.put("coverHidden", true);
            return List.of(row);
        });
        var result = new HomeService(jdbc).dashboard(User.builder().id(42L).build());
        assertThat(result.recentlyAdded().get(0).get("createdAt")).isEqualTo("2026-10-22T08:30");
        assertThat(result.recentlyAdded().get(0).get("coverHidden")).isEqualTo(true);
        assertThat(result.continuing()).isEmpty();
        assertThat(result.wanted()).isEmpty();
    }
}
