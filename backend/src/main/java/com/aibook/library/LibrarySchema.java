package com.aibook.library;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Additive, repeatable schema upgrade; existing books and progress stay untouched. */
@Component
@Order(30)
@RequiredArgsConstructor
public class LibrarySchema implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS personal_libraries (
                    id BIGSERIAL PRIMARY KEY,
                    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    name VARCHAR(50) NOT NULL,
                    description VARCHAR(500) NOT NULL DEFAULT '',
                    config TEXT NOT NULL DEFAULT '{}',
                    revision BIGINT NOT NULL DEFAULT 0,
                    sort_order BIGINT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(user_id, name)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS personal_library_groups (
                    id BIGSERIAL PRIMARY KEY,
                    library_id BIGINT NOT NULL REFERENCES personal_libraries(id) ON DELETE CASCADE,
                    name VARCHAR(50) NOT NULL,
                    sort_order BIGINT NOT NULL DEFAULT 0,
                    UNIQUE(library_id, name), UNIQUE(library_id, id)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS personal_library_items (
                    library_id BIGINT NOT NULL REFERENCES personal_libraries(id) ON DELETE CASCADE,
                    book_id BIGINT NOT NULL REFERENCES books(id) ON DELETE CASCADE,
                    group_id BIGINT,
                    favorite BOOLEAN NOT NULL DEFAULT FALSE,
                    favorite_at TIMESTAMP,
                    featured BOOLEAN NOT NULL DEFAULT FALSE,
                    sort_order BIGINT NOT NULL DEFAULT 0,
                    featured_order BIGINT NOT NULL DEFAULT 0,
                    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY(library_id, book_id),
                    FOREIGN KEY(library_id, group_id)
                        REFERENCES personal_library_groups(library_id, id)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS personal_library_lists (
                    id BIGSERIAL PRIMARY KEY,
                    library_id BIGINT NOT NULL REFERENCES personal_libraries(id) ON DELETE CASCADE,
                    name VARCHAR(50) NOT NULL,
                    description VARCHAR(500) NOT NULL DEFAULT '',
                    recommended BOOLEAN NOT NULL DEFAULT FALSE,
                    sort_order BIGINT NOT NULL DEFAULT 0,
                    UNIQUE(library_id, name), UNIQUE(library_id, id)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS personal_library_list_items (
                    library_id BIGINT NOT NULL,
                    list_id BIGINT NOT NULL,
                    book_id BIGINT NOT NULL,
                    sort_order BIGINT NOT NULL DEFAULT 0,
                    PRIMARY KEY(list_id, book_id),
                    FOREIGN KEY(library_id, list_id)
                        REFERENCES personal_library_lists(library_id, id) ON DELETE CASCADE,
                    FOREIGN KEY(library_id, book_id)
                        REFERENCES personal_library_items(library_id, book_id) ON DELETE CASCADE
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS personal_library_preferences (
                    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
                    page_size INTEGER NOT NULL DEFAULT 24
                )
                """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_personal_library_user_order "
                + "ON personal_libraries(user_id, sort_order, id)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_personal_library_book "
                + "ON personal_library_items(book_id)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_personal_library_group_order "
                + "ON personal_library_items(library_id, group_id, sort_order, book_id)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_personal_library_added "
                + "ON personal_library_items(library_id, added_at DESC, book_id)");
    }
}
