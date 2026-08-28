package com.aibook.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

class UserPreferenceTableMigrationInitializerTest {

    @Test
    void copiesLegacyValuesBeforeDroppingUserColumns() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UserPreferenceTableMigrationInitializer initializer = spy(
                new UserPreferenceTableMigrationInitializer(jdbcTemplate));
        doReturn(Set.of("id", "web_theme", "library_page_size"))
                .when(initializer).tableColumns("users");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 7L);
        row.put("web_theme", "natural");
        row.put("library_page_size", 30);
        when(jdbcTemplate.queryForList(
                "SELECT id, web_theme, library_page_size FROM users"))
                .thenReturn(List.of(row));
        when(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_preferences WHERE user_id = ?",
                Integer.class,
                7L)).thenReturn(0);
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class))
                .thenReturn(1L);
        when(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_preferences", Long.class))
                .thenReturn(1L);

        initializer.run(mock(ApplicationArguments.class));

        verify(jdbcTemplate).update(
                contains("INSERT INTO user_preferences"),
                any(Object[].class));
        verify(jdbcTemplate).execute("ALTER TABLE users DROP COLUMN web_theme");
        verify(jdbcTemplate).execute("ALTER TABLE users DROP COLUMN library_page_size");
    }

    @Test
    void keepsLegacyColumnsWhenMigrationCountIsIncomplete() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UserPreferenceTableMigrationInitializer initializer = spy(
                new UserPreferenceTableMigrationInitializer(jdbcTemplate));
        doReturn(Set.of("id", "web_theme")).when(initializer).tableColumns("users");
        when(jdbcTemplate.queryForList("SELECT id, web_theme FROM users"))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class))
                .thenReturn(1L);
        when(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_preferences", Long.class))
                .thenReturn(0L);

        assertThrows(
                IllegalStateException.class,
                () -> initializer.run(mock(ApplicationArguments.class)));
        verify(jdbcTemplate, never()).execute(eq("ALTER TABLE users DROP COLUMN web_theme"));
    }
}
