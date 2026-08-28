package com.aibook.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 将 users 表中的历史偏好列迁移到 user_preferences，并移除旧列。 */
@Component
@Order(20)
@RequiredArgsConstructor
@Slf4j
public class UserPreferenceTableMigrationInitializer implements ApplicationRunner {

    private static final List<String> PREFERENCE_COLUMNS = List.of(
            "book_preferences",
            "web_theme",
            "modern_theme_color",
            "warm_theme_color",
            "natural_theme_color",
            "macos26theme_color",
            "theme_background_settings",
            "library_view_mode",
            "library_page_size",
            "library_list_page_size",
            "scan_thread_count",
            "scheduled_scan_enabled",
            "scheduled_scan_time",
            "trash_retention_days",
            "dock_size",
            "dock_opacity",
            "dock_magnification",
            "dock_blur",
            "dock_icon_style",
            "ui_font_id",
            "reader_font_id",
            "all_book_covers_hidden",
            "book_cover_visibility_overrides",
            "all_random_covers_hidden",
            "random_cover_visibility_overrides");

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Set<String> userColumns = tableColumns("users");
        List<String> legacyColumns = PREFERENCE_COLUMNS.stream()
                .filter(userColumns::contains)
                .toList();
        if (legacyColumns.isEmpty()) return;

        migrateRows(legacyColumns);
        verifyMigration();
        legacyColumns.forEach(column ->
                jdbcTemplate.execute("ALTER TABLE users DROP COLUMN " + column));
        log.info("用户偏好已迁移至 user_preferences，移除 users 历史偏好列 {} 个", legacyColumns.size());
    }

    Set<String> tableColumns(String tableName) {
        return jdbcTemplate.queryForList(
                        "SELECT column_name FROM information_schema.columns "
                                + "WHERE table_schema = current_schema() AND table_name = ?",
                        String.class,
                        tableName)
                .stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private void migrateRows(List<String> columns) {
        String selectSql = "SELECT id, " + String.join(", ", columns) + " FROM users";
        List<Map<String, Object>> users = jdbcTemplate.queryForList(selectSql);
        for (Map<String, Object> row : users) {
            Number userId = (Number) row.get("id");
            if (userId == null) continue;

            Map<String, Object> values = new LinkedHashMap<>();
            columns.forEach(column -> values.put(column, row.get(column)));
            Integer existing = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_preferences WHERE user_id = ?",
                    Integer.class,
                    userId.longValue());
            if (existing != null && existing > 0) updateExisting(userId.longValue(), values);
            else insertNew(userId.longValue(), values);
        }
    }

    private void insertNew(long userId, Map<String, Object> values) {
        List<String> columns = new ArrayList<>(values.keySet());
        String placeholders = columns.stream().map(column -> "?").collect(Collectors.joining(", "));
        List<Object> parameters = new ArrayList<>();
        parameters.add(userId);
        columns.forEach(column -> parameters.add(values.get(column)));
        jdbcTemplate.update(
                "INSERT INTO user_preferences (user_id, " + String.join(", ", columns)
                        + ", created_at, updated_at) VALUES (?, " + placeholders
                        + ", CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                parameters.toArray());
    }

    private void updateExisting(long userId, Map<String, Object> values) {
        List<String> columns = new ArrayList<>(values.keySet());
        String assignments = columns.stream()
                .map(column -> column + " = COALESCE(" + column + ", ?)")
                .collect(Collectors.joining(", "));
        List<Object> parameters = new ArrayList<>();
        columns.forEach(column -> parameters.add(values.get(column)));
        parameters.add(userId);
        jdbcTemplate.update(
                "UPDATE user_preferences SET " + assignments + " WHERE user_id = ?",
                parameters.toArray());
    }

    private void verifyMigration() {
        Long userCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class);
        Long preferenceCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_preferences", Long.class);
        if (userCount == null || preferenceCount == null || preferenceCount < userCount) {
            throw new IllegalStateException("用户偏好迁移校验失败，保留 users 表历史偏好列");
        }
    }
}
