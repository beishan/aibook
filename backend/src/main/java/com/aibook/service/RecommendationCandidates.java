package com.aibook.service;

import com.aibook.dto.recommendation.RecommendationFilter;
import com.aibook.model.entity.User;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Select IDs only; EXISTS avoids duplicate candidates from multi-valued joins. */
@Component
@RequiredArgsConstructor
public class RecommendationCandidates {
    private final NamedParameterJdbcTemplate jdbc;
    private final CategoryService categories;

    static final String VISIBLE = "b.user_id = :user AND b.deleted_at IS NULL AND "
            + "(NOT EXISTS (SELECT 1 FROM book_scan_sources s WHERE s.book_id = b.id) "
            + "OR EXISTS (SELECT 1 FROM book_scan_sources s JOIN scan_directories d "
            + "ON d.id = s.scan_directory_id WHERE s.book_id = b.id "
            + "AND (d.library_visible = true OR d.library_visible IS NULL)))";

    public List<Long> ids(User user, RecommendationFilter f, LocalDate date) {
        var args = new MapSqlParameterSource("user", user.getId());
        var sql = new StringBuilder("SELECT b.id FROM books b WHERE " + VISIBLE);
        List<Long> categoryIds = f.categoryIds();
        if (categoryIds != null && f.includeSubcategories()) {
            var expanded = new LinkedHashSet<Long>();
            for (Long id : categoryIds) {
                try {
                    expanded.addAll(categories.getCategoryAndDescendantIds(id, user));
                } catch (org.springframework.web.server.ResponseStatusException exception) {
                    if (exception.getStatusCode().value() != 404) throw exception;
                    // Deleted categories remain nonmatching filters, never broaden to all books.
                    expanded.add(id);
                }
            }
            categoryIds = List.copyOf(expanded);
        }
        in(sql, args, "b.category_id", "categories", categoryIds);
        in(sql, args, "b.shelf_group_id", "groups", f.shelfGroupIds());
        in(sql, args, "b.reading_status", "statuses", f.statuses());
        in(sql, args, "LOWER(b.format)", "formats", f.formats());
        in(sql, args, "b.source_type", "sources", f.sources());
        bool(sql, args, "b.is_favorite", "favorite", f.favorite());
        bool(sql, args, "b.is_wanted", "wanted", f.wanted());
        bool(sql, args, "b.on_shelf", "onShelf", f.onShelf());
        if (nonempty(f.authorIds())) {
            sql.append(" AND EXISTS (SELECT 1 FROM book_authors a WHERE a.book_id=b.id AND a.author_id IN (:authors))");
            args.addValue("authors", f.authorIds());
        }
        if (nonempty(f.tagIds())) {
            if (f.matchAllTags()) {
                sql.append(" AND (SELECT COUNT(DISTINCT t.tag_id) FROM book_tags t WHERE t.book_id=b.id AND t.tag_id IN (:tags)) = :tagCount");
                args.addValue("tagCount", new HashSet<>(f.tagIds()).size());
            } else {
                sql.append(" AND EXISTS (SELECT 1 FROM book_tags t WHERE t.book_id=b.id AND t.tag_id IN (:tags))");
            }
            args.addValue("tags", f.tagIds());
        }
        if (nonempty(f.excludedTagIds())) {
            sql.append(" AND NOT EXISTS (SELECT 1 FROM book_tags t WHERE t.book_id=b.id AND t.tag_id IN (:excluded))");
            args.addValue("excluded", f.excludedTagIds());
        }
        if (f.addedWithinDays() != null) {
            sql.append(" AND b.created_at >= :addedSince");
            args.addValue("addedSince", date.minusDays(f.addedWithinDays()).atStartOfDay());
        }
        if (f.addedBeforeDays() != null) {
            sql.append(" AND b.created_at < :addedBefore");
            args.addValue("addedBefore", date.minusDays(f.addedBeforeDays()).atStartOfDay());
        }
        if (f.notReadDays() != null) {
            sql.append(" AND NOT EXISTS (SELECT 1 FROM reading_progress p WHERE p.book_id=b.id AND p.user_id=:user AND p.last_read_at >= :readSince)");
            args.addValue("readSince", date.minusDays(f.notReadDays()).atStartOfDay());
        }
        sql.append(" ORDER BY b.id");
        return jdbc.queryForList(sql.toString(), args, Long.class);
    }

    public List<Map<String, Object>> books(User user, List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return jdbc.queryForList("SELECT b.id, b.title, b.author, b.cover_url AS \"coverUrl\", "
                + "b.cover_hidden AS \"coverHidden\", b.reading_status AS \"readingStatus\", b.format FROM books b WHERE "
                + VISIBLE + " AND b.id IN (:ids)",
                new MapSqlParameterSource("user", user.getId()).addValue("ids", ids));
    }

    public Map<String, Object> options(User user) {
        var result = new LinkedHashMap<String, Object>();
        // Table names are fixed application literals, never request input.
        for (String table : List.of("categories", "tags", "authors", "shelf_groups")) {
            result.put(table, jdbc.queryForList("SELECT id, name FROM " + table
                    + " WHERE user_id=:user ORDER BY name, id", Map.of("user", user.getId())));
        }
        return result;
    }

    private static boolean nonempty(Collection<?> values) {
        return values != null && !values.isEmpty();
    }

    private static void in(StringBuilder sql, MapSqlParameterSource args,
            String column, String key, Collection<?> values) {
        if (!nonempty(values)) return;
        sql.append(" AND ").append(column).append(" IN (:").append(key).append(")");
        args.addValue(key, values);
    }

    private static void bool(StringBuilder sql, MapSqlParameterSource args,
            String column, String key, Boolean value) {
        if (value == null) return;
        sql.append(" AND COALESCE(").append(column).append(", false) = :").append(key);
        args.addValue(key, value);
    }
}
