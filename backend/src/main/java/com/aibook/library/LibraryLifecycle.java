package com.aibook.library;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Runs in the existing purge/merge transaction, before source book relationships disappear. */
@Service
@RequiredArgsConstructor
public class LibraryLifecycle {
    private final JdbcTemplate jdbc;

    @Transactional
    public void purge(long bookId) {
        jdbc.update("DELETE FROM personal_library_items WHERE book_id=?", bookId);
    }

    @Transactional
    public void merge(long targetId, long sourceId) {
        // Match the owner lock used by creation/settings; serialize merge against all affected libraries.
        jdbc.queryForList("SELECT u.id FROM users u JOIN books b ON b.user_id=u.id WHERE b.id=? FOR UPDATE OF u", targetId);
        jdbc.queryForList("SELECT l.id FROM personal_libraries l JOIN books b ON b.user_id=l.user_id "
                + "WHERE b.id=? ORDER BY l.id FOR UPDATE OF l", targetId);
        jdbc.update("""
                INSERT INTO personal_library_items
                    (library_id,book_id,group_id,favorite,favorite_at,featured,sort_order,featured_order,added_at)
                SELECT library_id,?,group_id,favorite,favorite_at,featured,sort_order,featured_order,added_at
                FROM personal_library_items WHERE book_id=?
                ON CONFLICT(library_id,book_id) DO UPDATE SET
                    favorite=personal_library_items.favorite OR EXCLUDED.favorite,
                    favorite_at=COALESCE(personal_library_items.favorite_at,EXCLUDED.favorite_at),
                    featured=personal_library_items.featured OR EXCLUDED.featured,
                    featured_order=CASE WHEN personal_library_items.featured
                        THEN personal_library_items.featured_order ELSE EXCLUDED.featured_order END,
                    added_at=LEAST(personal_library_items.added_at,EXCLUDED.added_at)
                """, targetId, sourceId);
        jdbc.update("""
                INSERT INTO personal_library_list_items(library_id,list_id,book_id,sort_order)
                SELECT library_id,list_id,?,sort_order FROM personal_library_list_items WHERE book_id=?
                ON CONFLICT(list_id,book_id) DO NOTHING
                """, targetId, sourceId);
        purge(sourceId);
    }
}
