package com.aibook.audiobook;

import static com.aibook.audiobook.AudioStore.number;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.ScanDirectoryRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AudiobookService {
    private final AudioStore store;
    private final ObjectMapper mapper;
    private final BookRepository books;
    private final ScanDirectoryRepository directories;

    @Transactional(readOnly = true)
    public Map<String, Object> list(User user, String keyword, String status, int page, int size, boolean favorite) {
        boolean recycled = "RECYCLED".equals(status);
        String filter = " FROM audiobook_editions e JOIN books b ON b.id=e.book_id "
                + "LEFT JOIN listening_progress p ON p.edition_id=e.id AND p.user_id=b.user_id "
                + "WHERE b.user_id=? AND b.deleted_at IS NULL AND e.deleted_at IS " + (recycled ? "NOT NULL " : "NULL ")
                + "AND (NOT EXISTS(SELECT 1 FROM book_scan_sources s WHERE s.book_id=b.id) "
                + "OR EXISTS(SELECT 1 FROM book_scan_sources s JOIN scan_directories d ON d.id=s.scan_directory_id WHERE s.book_id=b.id AND d.library_visible=true)) "
                + "AND (LOWER(b.title) LIKE ? OR LOWER(e.narrator) LIKE ? OR LOWER(COALESCE(b.author,'')) LIKE ?) "
                + "AND (?='' OR COALESCE(p.status,'NEW')=?) AND (?=false OR b.is_favorite=true) ";
        String pattern = "%" + (keyword == null ? "" : keyword.trim().toLowerCase(java.util.Locale.ROOT)) + "%";
        String selected = status == null || recycled ? "" : status;
        if (!List.of("", "NEW", "LISTENING", "FINISHED").contains(selected)) {
            throw new IllegalArgumentException("收听状态无效");
        }
        var params = new Object[]{user.getId(), pattern, pattern, pattern, selected, selected, favorite};
        var count = store.one("SELECT COUNT(*) AS total" + filter, params);
        var args = new ArrayList<>(List.of(params));
        args.add(size); args.add((long) page * size);
        var rows = store.rows("SELECT e.id,e.book_id,e.name,e.narrator,e.language,b.title,b.author,"
                + "b.cover_url,b.cover_hidden,b.format AS ebook_format,COALESCE(p.status,'NEW') AS status,"
                + "p.track_id,p.position_ms,p.updated_at,"
                + "(SELECT COUNT(*) FROM audio_tracks t WHERE t.edition_id=e.id) AS track_count,"
                + "(SELECT COALESCE(SUM(duration_ms),0) FROM audio_tracks t WHERE t.edition_id=e.id) AS duration_ms"
                + filter + "ORDER BY p.updated_at DESC NULLS LAST,e.id DESC LIMIT ? OFFSET ?", args.toArray());
        return Map.of("content", rows, "totalElements", number(count, "total"), "page", page, "size", size);
    }

    public List<Map<String, Object>> byBook(User user, long bookId) {
        books.findByIdAndUserAndDeletedAtIsNull(bookId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "作品不存在"));
        return store.rows("SELECT id,name,narrator FROM audiobook_editions WHERE book_id=? AND deleted_at IS NULL ORDER BY id", bookId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(User user, long id) {
        var edition = store.edition(user.getId(), id);
        var tracks = tracks(id);
        var publicTracks = tracks.stream().map(track -> {
            var result = new java.util.LinkedHashMap<>(track);
            result.remove("filePath"); result.remove("sourceRoot"); result.remove("fileHash");
            result.remove("extractedChapters");
            try { checkedPath(track); result.put("available", true); }
            catch (IOException | ResponseStatusException exception) { result.put("available", false); }
            return result;
        }).toList();
        edition.put("tracks", publicTracks);
        edition.put("durationMs", tracks.stream().mapToLong(t -> number(t, "durationMs")).sum());
        edition.put("chapters", store.rows("SELECT * FROM audio_chapters WHERE edition_id=? ORDER BY sort_order,id", id)
                .stream().map(row -> {
                    row.put("segments", read(row.get("segments").toString(), new TypeReference<List<AudioTypes.Segment>>() {}));
                    return row;
                }).toList());
        edition.put("progress", progress(user.getId(), id));
        edition.put("bookmarks", bookmarks(user, id));
        edition.put("editions", store.rows("SELECT id,name,narrator FROM audiobook_editions "
                + "WHERE book_id=? AND deleted_at IS NULL ORDER BY id", edition.get("bookId")));
        return edition;
    }

    public Map<String,Object> downloadTrack(User user,long id,long trackId) {
        store.edition(user.getId(),id);
        return store.one("SELECT * FROM audio_tracks WHERE id=? AND edition_id=?",trackId,id);
    }

    public List<Map<String, Object>> tracks(long editionId) {
        return store.rows("SELECT * FROM audio_tracks WHERE edition_id=? ORDER BY sort_order,id", editionId);
    }

    public Map<String, Object> progress(long userId, long editionId) {
        var rows = store.rows("SELECT * FROM listening_progress WHERE user_id=? AND edition_id=?", userId, editionId);
        return rows.isEmpty() ? Map.of("revision", 0, "positionMs", 0, "status", "NEW") : rows.getFirst();
    }

    @Transactional
    public Map<String, Object> edit(User user, long id, AudioTypes.EditionRequest request) {
        var edition = store.edition(user.getId(), id);
        if (request.bookId() != null && request.bookId() != number(edition, "bookId")) {
            books.findByIdAndUserAndDeletedAtIsNull(request.bookId(), user)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "关联作品不存在"));
            store.update("UPDATE audiobook_editions SET book_id=? WHERE id=?", request.bookId(), id);
        }
        store.update("UPDATE audiobook_editions SET name=?,narrator=?,language=?,description=? WHERE id=?",
                request.name(), clean(request.narrator()), clean(request.language()), clean(request.description()), id);
        return detail(user, id);
    }

    @Transactional
    public void recycle(User user, long id, boolean restore) {
        if (restore) {
            store.one("SELECT e.id FROM audiobook_editions e JOIN books b ON b.id=e.book_id "
                    + "WHERE e.id=? AND b.user_id=? AND b.deleted_at IS NULL", id, user.getId());
        } else store.edition(user.getId(), id);
        store.update("UPDATE audiobook_editions SET deleted_at=? WHERE id=?",
                restore ? null : java.sql.Timestamp.from(Instant.now()), id);
        store.update("UPDATE listening_sessions SET ended=true WHERE edition_id=?", id);
    }

    @Transactional
    public void reorder(User user, long id, List<Long> order) {
        store.edition(user.getId(), id);
        var actual = tracks(id).stream().map(t -> number(t, "id")).toList();
        if (order.size() != actual.size() || order.stream().distinct().count() != actual.size()
                || !order.containsAll(actual)) throw new IllegalArgumentException("必须包含全部音轨且不能重复");
        for (int index = 0; index < order.size(); index++) {
            store.update("UPDATE audio_tracks SET sort_order=? WHERE id=? AND edition_id=?", index, order.get(index), id);
        }
        bump(id);
    }

    @Transactional
    public void editTrack(User user, long id, long trackId, String title) {
        store.edition(user.getId(), id);
        if (store.update("UPDATE audio_tracks SET title=? WHERE id=? AND edition_id=?", title, trackId, id) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "音轨不存在");
        }
    }

    @Transactional
    public void chapters(User user, long id, List<AudioTypes.ChapterEdit> chapters) {
        store.edition(user.getId(), id);
        if (chapters.size() > 10000) throw new IllegalArgumentException("章节过多");
        for (var chapter : chapters) {
            for (var segment : chapter.segments()) {
                var track = store.one("SELECT duration_ms FROM audio_tracks WHERE id=? AND edition_id=?", segment.trackId(), id);
                if (segment.startMs() >= segment.endMs() || segment.endMs() > number(track, "durationMs")) {
                    throw new IllegalArgumentException("章节时间超出音轨");
                }
            }
        }
        store.update("DELETE FROM audio_chapters WHERE edition_id=?", id);
        for (int index = 0; index < chapters.size(); index++) {
            var chapter = chapters.get(index);
            store.update("INSERT INTO audio_chapters(edition_id,title,sort_order,segments) VALUES(?,?,?,?)",
                    id, chapter.title(), index, json(chapter.segments()));
        }
        bump(id);
    }

    public void bump(long id) {
        store.update("UPDATE audiobook_editions SET content_revision=content_revision+1 WHERE id=?", id);
        store.update("UPDATE listening_sessions SET ended=true WHERE edition_id=?", id);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> bookmarks(User user, long id) {
        store.edition(user.getId(), id);
        return store.rows("SELECT * FROM audio_bookmarks WHERE user_id=? AND edition_id=? ORDER BY created_at DESC",
                user.getId(), id);
    }

    @Transactional
    public void bookmark(User user, long id, AudioTypes.BookmarkRequest request) {
        store.edition(user.getId(), id);
        var track = store.one("SELECT duration_ms FROM audio_tracks WHERE id=? AND edition_id=?", request.trackId(), id);
        if (request.positionMs() > number(track, "durationMs")) throw new IllegalArgumentException("书签位置超出音轨");
        store.update("INSERT INTO audio_bookmarks(user_id,edition_id,track_id,position_ms,title) VALUES(?,?,?,?,?)",
                user.getId(), id, request.trackId(), request.positionMs(), request.title());
    }

    @Transactional
    public void deleteBookmark(User user, long bookmarkId) {
        if (store.update("DELETE FROM audio_bookmarks WHERE id=? AND user_id=?", bookmarkId, user.getId()) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "书签不存在");
        }
    }

    public Path checkedPath(Map<String, Object> track) throws IOException {
        Path root = Path.of(track.get("sourceRoot").toString()).toRealPath();
        Path file = Path.of(track.get("filePath").toString()).toRealPath();
        if (!file.startsWith(root) || !Files.isRegularFile(file) || !Files.isReadable(file)) {
            throw new IOException("音轨不在允许目录内或已缺失");
        }
        if (Files.size(file) != number(track, "fileSize")
                || Files.getLastModifiedTime(file).toMillis() != number(track, "modifiedMs")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "音频已变化，请重新探测确认");
        }
        return file;
    }

    public String json(Object object) {
        try { return mapper.writeValueAsString(object); }
        catch (IOException exception) { throw new IllegalArgumentException("音频资料编码失败", exception); }
    }

    public <T> T read(String value, TypeReference<T> type) {
        try { return mapper.readValue(value, type); }
        catch (IOException exception) { throw new IllegalArgumentException("音频资料解析失败", exception); }
    }

    static String clean(String value) { return value == null ? "" : value.trim(); }

    static String digest(Path path) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("任务已取消");
                    digest.update(buffer, 0, count);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
