-- Additive, idempotent PostgreSQL schema. Original media files are never deleted.
ALTER TABLE books ALTER COLUMN format DROP NOT NULL;
ALTER TABLE books ALTER COLUMN file_path DROP NOT NULL;
ALTER TABLE user_preferences ADD COLUMN IF NOT EXISTS audio_settings TEXT;
CREATE TABLE IF NOT EXISTS audiobook_editions (
 id BIGSERIAL PRIMARY KEY, book_id BIGINT NOT NULL REFERENCES books(id),
 name VARCHAR(200) NOT NULL, narrator VARCHAR(200) NOT NULL DEFAULT '',
 language VARCHAR(80) NOT NULL DEFAULT '中文', description TEXT NOT NULL DEFAULT '',
 content_revision BIGINT NOT NULL DEFAULT 1, deleted_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_audio_edition_book ON audiobook_editions(book_id);
CREATE TABLE IF NOT EXISTS audio_tracks (
 id BIGSERIAL PRIMARY KEY, edition_id BIGINT NOT NULL REFERENCES audiobook_editions(id),
 title VARCHAR(500) NOT NULL, file_path TEXT NOT NULL, source_root TEXT NOT NULL,
 sort_order INTEGER NOT NULL, duration_ms BIGINT NOT NULL CHECK(duration_ms > 0),
 file_size BIGINT NOT NULL, modified_ms BIGINT NOT NULL, file_hash VARCHAR(64) NOT NULL,
 mime_type VARCHAR(100) NOT NULL, codec VARCHAR(80) NOT NULL,
 extracted_chapters TEXT NOT NULL DEFAULT '[]',
 UNIQUE(edition_id, file_path)
);
CREATE INDEX IF NOT EXISTS ix_audio_track_order ON audio_tracks(edition_id, sort_order, id);
CREATE TABLE IF NOT EXISTS audio_chapters (
 id BIGSERIAL PRIMARY KEY, edition_id BIGINT NOT NULL REFERENCES audiobook_editions(id),
 title VARCHAR(500) NOT NULL, sort_order INTEGER NOT NULL, segments TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS listening_progress (
 user_id BIGINT NOT NULL REFERENCES users(id), edition_id BIGINT NOT NULL REFERENCES audiobook_editions(id),
 track_id BIGINT REFERENCES audio_tracks(id), position_ms BIGINT NOT NULL DEFAULT 0,
 revision BIGINT NOT NULL DEFAULT 0, active_session VARCHAR(64),
 status VARCHAR(20) NOT NULL DEFAULT 'NEW', updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(user_id, edition_id)
);
CREATE TABLE IF NOT EXISTS listening_sessions (
 id VARCHAR(64) PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id),
 edition_id BIGINT NOT NULL REFERENCES audiobook_editions(id), credential_hash VARCHAR(64) NOT NULL,
 content_revision BIGINT NOT NULL, last_sequence BIGINT NOT NULL DEFAULT -1,
 elapsed_ms BIGINT NOT NULL DEFAULT 0, last_event_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 expires_at TIMESTAMPTZ NOT NULL, ended BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE IF NOT EXISTS audio_bookmarks (
 id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id),
 edition_id BIGINT NOT NULL REFERENCES audiobook_editions(id),
 track_id BIGINT NOT NULL REFERENCES audio_tracks(id), position_ms BIGINT NOT NULL,
 title VARCHAR(200) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS listening_daily_activity (
 user_id BIGINT NOT NULL REFERENCES users(id), activity_date DATE NOT NULL,
 elapsed_ms BIGINT NOT NULL DEFAULT 0, PRIMARY KEY(user_id, activity_date)
);
CREATE TABLE IF NOT EXISTS audio_import_tasks (
 id VARCHAR(64) PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id),
 directory_id BIGINT REFERENCES scan_directories(id) ON DELETE SET NULL,
 state VARCHAR(30) NOT NULL, payload TEXT NOT NULL DEFAULT '[]',
 error TEXT, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_audio_import_user ON audio_import_tasks(user_id, created_at);
ALTER TABLE audio_import_tasks ADD COLUMN IF NOT EXISTS result_edition_id BIGINT REFERENCES audiobook_editions(id);
CREATE TABLE IF NOT EXISTS audio_offline_events (
 user_id BIGINT NOT NULL REFERENCES users(id), event_id VARCHAR(64) NOT NULL,
 edition_id BIGINT NOT NULL REFERENCES audiobook_editions(id),
 applied_revision BIGINT NOT NULL, PRIMARY KEY(user_id,event_id)
);
CREATE TABLE IF NOT EXISTS audio_listening_intervals (
 user_id BIGINT NOT NULL REFERENCES users(id), start_ms BIGINT NOT NULL, end_ms BIGINT NOT NULL,
 CHECK(end_ms>start_ms), PRIMARY KEY(user_id,start_ms,end_ms)
);
CREATE INDEX IF NOT EXISTS ix_audio_listening_intervals ON audio_listening_intervals(user_id,end_ms,start_ms);
ALTER TABLE audio_tracks ADD COLUMN IF NOT EXISTS disc_number INTEGER;
ALTER TABLE audio_tracks ADD COLUMN IF NOT EXISTS track_number INTEGER;
