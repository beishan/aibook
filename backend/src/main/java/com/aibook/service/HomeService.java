package com.aibook.service;

import com.aibook.model.entity.User;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bounded, account-scoped home queries; no whole-library entity loading. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {
    private final NamedParameterJdbcTemplate jdbc;

    public record Dashboard(Map<String, Object> overview, List<Map<String, Object>> categories,
            List<Map<String, Object>> continuing, List<Map<String, Object>> recentlyRead,
            List<Map<String, Object>> recentlyAdded, List<Map<String, Object>> recentlyUpdated,
            List<Map<String, Object>> wanted) {}

    private static final String COLUMNS = "b.id, b.title, b.author, b.format, "
            + "b.cover_url AS \"coverUrl\", b.cover_hidden AS \"coverHidden\", b.rating, b.reading_status AS \"readingStatus\", "
            + "b.created_at AS \"createdAt\", b.updated_at AS \"updatedAt\"";
    private static final String PROGRESS_COLUMNS = ", p.total_progress AS \"totalProgress\", "
            + "p.current_chapter_title AS \"currentChapterTitle\", p.last_read_at AS \"lastReadAt\"";

    public Dashboard dashboard(User user) {
        var args = new MapSqlParameterSource("user", user.getId());
        String visible = RecommendationCandidates.VISIBLE;
        var overview = jdbc.queryForMap("SELECT COUNT(*) AS \"totalBooks\", "
                + "COALESCE(SUM(CASE WHEN b.reading_status='READING' THEN 1 ELSE 0 END),0) AS \"readingBooks\", "
                + "COALESCE(SUM(CASE WHEN b.reading_status='FINISHED' THEN 1 ELSE 0 END),0) AS \"finishedBooks\", "
                + "COALESCE(SUM(CASE WHEN b.is_favorite=true THEN 1 ELSE 0 END),0) AS \"favoriteBooks\", "
                + "COALESCE(SUM(CASE WHEN b.is_wanted=true THEN 1 ELSE 0 END),0) AS \"wantedBooks\", "
                + "COALESCE(SUM(b.file_size),0) AS \"fileSizeBytes\" FROM books b WHERE " + visible, args);
        var categories = jdbc.queryForList("SELECT c.id, c.name, COUNT(*) AS count "
                + "FROM books b JOIN categories c ON c.id=b.category_id AND c.user_id=:user WHERE "
                + visible + " GROUP BY c.id, c.name ORDER BY count DESC, c.id LIMIT 8", args);
        String reading = "SELECT " + COLUMNS + PROGRESS_COLUMNS
                + " FROM books b JOIN reading_progress p ON p.book_id=b.id AND p.user_id=:user WHERE "
                + visible + " AND p.last_read_at IS NOT NULL";
        var continuing = bookRows(reading + " AND b.reading_status <> 'FINISHED' "
                + "ORDER BY p.last_read_at DESC, b.id DESC LIMIT 3", args);
        var read = bookRows(reading + " ORDER BY p.last_read_at DESC, b.id DESC LIMIT 4", args);
        String books = "SELECT " + COLUMNS + " FROM books b WHERE " + visible;
        var added = bookRows(books + " ORDER BY b.created_at DESC, b.id DESC LIMIT 6", args);
        var updated = bookRows(books
                + " AND b.updated_at > b.created_at ORDER BY b.updated_at DESC, b.id DESC LIMIT 5", args);
        var wanted = bookRows(books
                + " AND b.is_wanted=true ORDER BY b.created_at DESC, b.id DESC LIMIT 4", args);
        return new Dashboard(overview, categories, continuing, read, added, updated, wanted);
    }
    private List<Map<String, Object>> bookRows(String sql, MapSqlParameterSource args) {
        var rows = jdbc.queryForList(sql, args);
        for (var row : rows) {
            row.replaceAll((key, value) -> value instanceof java.sql.Timestamp timestamp
                    ? timestamp.toLocalDateTime().toString() : value);
        }
        return rows;
    }

}
