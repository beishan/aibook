package com.aibook.audiobook;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
@DependsOn("audioSchema")
@RequiredArgsConstructor
public class AudioStore {
    private final JdbcTemplate jdbc;

    public List<Map<String, Object>> rows(String sql, Object... args) {
        return jdbc.query(sql, (result, index) -> {
            var row = new LinkedHashMap<String, Object>();
            var metadata = result.getMetaData();
            for (int column = 1; column <= metadata.getColumnCount(); column++) {
                Object value = result.getObject(column);
                if (value instanceof java.sql.Timestamp timestamp) value = timestamp.toInstant().toString();
                row.put(JdbcUtils.convertUnderscoreNameToPropertyName(metadata.getColumnLabel(column)), value);
            }
            return row;
        }, args);
    }

    public Map<String, Object> one(String sql, Object... args) {
        var result = rows(sql, args);
        if (result.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "有声书记录不存在");
        return result.getFirst();
    }

    public int update(String sql, Object... args) { return jdbc.update(sql, args); }

    public long id(String sql, Object... args) {
        return ((Number) one(sql, args).get("id")).longValue();
    }

    public Map<String, Object> edition(long userId, long editionId) {
        return one("SELECT e.*, b.title, b.author, b.cover_url, b.cover_hidden, b.user_id, "
                + "b.format AS ebook_format FROM audiobook_editions e JOIN books b ON b.id=e.book_id "
                + "WHERE e.id=? AND b.user_id=? AND b.deleted_at IS NULL AND e.deleted_at IS NULL",
                editionId, userId);
    }

    public static long number(Map<String, Object> row, String key) {
        return row.get(key) == null ? 0 : ((Number) row.get(key)).longValue();
    }
}
