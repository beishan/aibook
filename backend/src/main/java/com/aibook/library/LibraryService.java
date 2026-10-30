package com.aibook.library;

import com.aibook.model.entity.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Account-owned, paged library projections. Reading data has no library dimension. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LibraryService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private static final Set<Integer> SIZES = Set.of(12, 24, 48, 96);
    private static final List<String> MODULES = List.of("continuing", "featured", "lists", "recent");
    private static final String VISIBLE = "b.user_id=:user AND b.deleted_at IS NULL "
            + "AND b.purged_at IS NULL AND b.format IS NOT NULL";
    private static final String BOOK_FROM = " FROM personal_library_items i "
            + "JOIN books b ON b.id=i.book_id "
            + "LEFT JOIN reading_progress p ON p.book_id=b.id AND p.user_id=:user ";
    private static final String BOOK_COLUMNS = "b.id,b.title,b.author,b.format,b.cover_url AS \"coverUrl\","
            + "b.cover_hidden AS \"coverHidden\",b.reading_status AS \"readingStatus\","
            + "b.category_id AS \"categoryId\",i.group_id AS \"groupId\",i.favorite,i.featured,"
            + "i.added_at AS \"addedAt\",p.total_progress AS \"totalProgress\","
            + "p.current_chapter_title AS \"currentChapterTitle\",p.last_read_at AS \"lastReadAt\"";

    public record Page(List<Map<String, Object>> content, long totalElements, int number, int size) {}
    public record BatchItem(long libraryId, long bookId, String status, String reason) {}

    private MapSqlParameterSource args(User user, long library) {
        return new MapSqlParameterSource("user", user.getId()).addValue("library", library);
    }

    public int pageSize(User user) {
        var values = jdbc.queryForList("SELECT page_size FROM personal_library_preferences WHERE user_id=:user",
                args(user, 0), Integer.class);
        return values.isEmpty() ? 24 : values.get(0);
    }

    @Transactional
    public void pageSize(User user, int size) {
        checkPage(0, size);
        jdbc.update("INSERT INTO personal_library_preferences(user_id,page_size) VALUES(:user,:size) "
                + "ON CONFLICT(user_id) DO UPDATE SET page_size=EXCLUDED.page_size",
                args(user, 0).addValue("size", size));
    }

    public Page libraries(User user, String keyword, int page, int size, String sort) {
        checkPage(page, size);
        var parameters = args(user, 0).addValue("query", search(keyword))
                .addValue("limit", size).addValue("offset", (long) page * size);
        String where = " WHERE l.user_id=:user AND l.name ILIKE :query ESCAPE '!'";
        String order = "created".equals(sort) ? "l.created_at DESC,l.id DESC" : "l.sort_order,l.id";
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM personal_libraries l" + where,
                parameters, Long.class);
        var rows = rows("SELECT l.*, (SELECT COUNT(*) FROM personal_library_items i "
                + "JOIN books b ON b.id=i.book_id WHERE i.library_id=l.id AND " + VISIBLE
                + ") AS \"bookCount\" FROM personal_libraries l" + where
                + " ORDER BY " + order + " LIMIT :limit OFFSET :offset", parameters);
        rows.forEach(this::decodeConfig);
        return new Page(rows, count, page, size);
    }

    public Map<String, Object> library(User user, long id) {
        Map<String, Object> library = owned(user, id, false);
        var parameters = args(user, id);
        library.put("bookCount", jdbc.queryForObject("SELECT COUNT(*) FROM personal_library_items i "
                + "JOIN books b ON b.id=i.book_id WHERE i.library_id=:library AND " + VISIBLE,
                parameters, Long.class));
        library.put("groups", rows("SELECT id,name,sort_order AS \"sortOrder\" "
                + "FROM personal_library_groups WHERE library_id=:library ORDER BY sort_order,id", parameters));
        library.put("lists", lists(user, id));
        return library;
    }

    Map<String, Object> owned(User user, long id, boolean lock) {
        var result = rows("SELECT * FROM personal_libraries WHERE id=:library AND user_id=:user"
                + (lock ? " FOR UPDATE" : ""), args(user, id));
        if (result.isEmpty()) throw error(HttpStatus.NOT_FOUND, "图书馆不存在或已删除");
        decodeConfig(result.get(0));
        return result.get(0);
    }

    @Transactional
    public Map<String, Object> create(User user, String name, String description, Map<String, Object> config) {
        String clean = name(name);
        // Serialize creates/renames for the same owner, including duplicate-name checks.
        lockOwner(user);
        unique(user, 0, "personal_libraries", 0, clean);
        var parameters = args(user, 0).addValue("name", clean)
                .addValue("description", description(description)).addValue("config", encode(config));
        Long id = jdbc.queryForObject("INSERT INTO personal_libraries(user_id,name,description,config,sort_order) "
                + "VALUES(:user,:name,:description,:config,"
                + "COALESCE((SELECT MAX(sort_order)+1 FROM personal_libraries WHERE user_id=:user),0)) "
                + "RETURNING id", parameters, Long.class);
        return library(user, id);
    }

    @Transactional
    public Map<String, Object> save(User user, long id, long revision, String name,
            String description, Map<String, Object> config) {
        lockOwner(user);
        var existing = owned(user, id, true);
        if (((Number) existing.get("revision")).longValue() != revision) {
            throw error(HttpStatus.CONFLICT, "其他设备已更新图书馆，请保留草稿并重新加载后修改");
        }
        String clean = name(name);
        unique(user, id, "personal_libraries", 0, clean);
        jdbc.update("UPDATE personal_libraries SET name=:name,description=:description,config=:config,"
                        + "revision=revision+1 WHERE id=:library AND user_id=:user AND revision=:revision",
                args(user, id).addValue("name", clean).addValue("description", description(description))
                        .addValue("config", encode(config)).addValue("revision", revision));
        return library(user, id);
    }

    @Transactional
    public void delete(User user, long id) {
        owned(user, id, true);
        jdbc.update("DELETE FROM personal_libraries WHERE id=:library AND user_id=:user", args(user, id));
    }

    public Page books(User user, long id, String query, Long groupId, Long listId, boolean favorites,
            String status, Long categoryId, Long tagId, String sort, int page, int size) {
        owned(user, id, false);
        checkPage(page, size);
        var parameters = args(user, id).addValue("query", search(query))
                .addValue("limit", size).addValue("offset", (long) page * size);
        String where = " WHERE i.library_id=:library AND " + VISIBLE
                + " AND (b.title ILIKE :query ESCAPE '!' OR COALESCE(b.author,'') ILIKE :query ESCAPE '!')";
        if (groupId != null) {
            where += groupId == 0 ? " AND i.group_id IS NULL" : " AND i.group_id=:group";
            parameters.addValue("group", groupId);
        }
        if (listId != null) {
            collection(user, id, "lists", listId);
            where += " AND EXISTS(SELECT 1 FROM personal_library_list_items li "
                    + "WHERE li.library_id=:library AND li.book_id=b.id AND li.list_id=:list)";
            parameters.addValue("list", listId);
        }
        if (favorites) where += " AND i.favorite=true";
        if (status != null && !status.isBlank()) {
            if (!Set.of("UNREADING", "READING", "FINISHED").contains(status)) {
                throw error(HttpStatus.BAD_REQUEST, "阅读状态无效");
            }
            where += " AND b.reading_status=:status";
            parameters.addValue("status", status);
        }
        if (categoryId != null) {
            where += " AND b.category_id=:category";
            parameters.addValue("category", categoryId);
        }
        if (tagId != null) {
            where += " AND EXISTS(SELECT 1 FROM book_tags bt WHERE bt.book_id=b.id AND bt.tag_id=:tag)";
            parameters.addValue("tag", tagId);
        }
        String order = switch (sort == null ? "" : sort) {
            case "title" -> "b.title,b.id";
            case "author" -> "b.author NULLS LAST,b.id";
            case "recent" -> "p.last_read_at DESC NULLS LAST,b.id";
            case "favorite" -> "i.favorite_at DESC NULLS LAST,b.id";
            case "manual" -> listId == null ? "i.sort_order,b.id" : "(SELECT li.sort_order "
                    + "FROM personal_library_list_items li WHERE li.list_id=:list AND li.book_id=b.id),b.id";
            default -> "i.added_at DESC,b.id DESC";
        };
        long total = jdbc.queryForObject("SELECT COUNT(*)" + BOOK_FROM + where, parameters, Long.class);
        return new Page(rows("SELECT " + BOOK_COLUMNS + BOOK_FROM + where + " ORDER BY " + order
                + " LIMIT :limit OFFSET :offset", parameters), total, page, size);
    }

    public List<Long> membership(User user, long id, List<Long> ids, Long listId) {
        owned(user, id, false);
        checkIds(ids, 200);
        var parameters = args(user, id).addValue("ids", ids).addValue("list", listId);
        if (listId != null) collection(user, id, "lists", listId);
        return jdbc.queryForList("SELECT book_id FROM "
                + (listId == null ? "personal_library_items" : "personal_library_list_items")
                + " WHERE library_id=:library AND book_id IN (:ids)"
                + (listId == null ? "" : " AND list_id=:list"), parameters, Long.class);
    }

    @Transactional
    public void items(User user, long id, List<Long> ids, Long groupId, Boolean favorite, boolean move) {
        checkIds(ids, 200);
        owned(user, id, true);
        for (long bookId : ids.stream().distinct().toList()) item(user, id, bookId, groupId, favorite, null, move);
    }

    public Map<String, Object> home(User user, long id) {
        owned(user, id, false);
        var parameters = args(user, id);
        String base = "SELECT " + BOOK_COLUMNS + BOOK_FROM
                + " WHERE i.library_id=:library AND " + VISIBLE;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("continuing", rows(base + " AND p.last_read_at IS NOT NULL "
                + "AND b.reading_status<>'FINISHED' ORDER BY p.last_read_at DESC,b.id LIMIT 12", parameters));
        result.put("featured", rows(base + " AND i.featured=true ORDER BY i.featured_order,b.id LIMIT 12", parameters));
        result.put("recent", rows(base + " ORDER BY i.added_at DESC,b.id DESC LIMIT 12", parameters));
        result.put("lists", lists(user, id).stream().filter(row -> Boolean.TRUE.equals(row.get("recommended")))
                .limit(6).toList());
        return result;
    }

    @Transactional
    public List<BatchItem> add(User user, List<Long> libraryIds, List<Long> bookIds, Long listId) {
        checkIds(libraryIds, 20);
        checkIds(bookIds, 200);
        List<Long> targets = libraryIds.stream().distinct().sorted().toList();
        if (listId != null && targets.size() != 1) {
            throw error(HttpStatus.BAD_REQUEST, "书单添加只支持一个目标馆");
        }
        List<BatchItem> results = new ArrayList<>();
        List<Long> available = new ArrayList<>();
        for (long id : targets) {
            try {
                owned(user, id, true);
                available.add(id);
            } catch (ResponseStatusException exception) {
                if (exception.getStatusCode() != HttpStatus.NOT_FOUND) throw exception;
                for (long bookId : bookIds.stream().distinct().toList()) {
                    results.add(new BatchItem(id, bookId, "failed", "图书馆不可用或不属于当前账户"));
                }
            }
        }
        if (available.isEmpty()) return results;
        if (listId != null) collection(user, available.get(0), "lists", listId);
        var parameters = args(user, 0).addValue("ids", bookIds);
        var valid = jdbc.queryForList("SELECT b.id FROM books b WHERE b.id IN (:ids) AND "
                + VISIBLE + " FOR SHARE", parameters, Long.class);
        for (long id : available) {
            for (long bookId : bookIds.stream().distinct().toList()) {
                if (!valid.contains(bookId)) {
                    results.add(new BatchItem(id, bookId, "failed", "书籍不可用或不属于当前账户"));
                    continue;
                }
                var params = args(user, id).addValue("book", bookId).addValue("list", listId);
                int inserted = jdbc.update("INSERT INTO personal_library_items(library_id,book_id,sort_order) "
                        + "VALUES(:library,:book,COALESCE((SELECT MAX(sort_order)+1 FROM personal_library_items "
                        + "WHERE library_id=:library),0)) ON CONFLICT DO NOTHING", params);
                int added = inserted;
                if (listId != null) added = jdbc.update("INSERT INTO personal_library_list_items(library_id,list_id,book_id,sort_order) "
                        + "VALUES(:library,:list,:book,COALESCE((SELECT MAX(sort_order)+1 "
                        + "FROM personal_library_list_items WHERE list_id=:list),0)) ON CONFLICT DO NOTHING", params);
                results.add(new BatchItem(id, bookId, added > 0 ? "added" : "existing", ""));
            }
        }
        return results;
    }

    @Transactional
    public void item(User user, long id, long bookId, Long groupId, Boolean favorite,
            Boolean featured, boolean move) {
        owned(user, id, true);
        var params = args(user, id).addValue("book", bookId);
        if (move && groupId != null) collection(user, id, "groups", groupId);
        if (featured != null && featured) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM personal_library_items "
                    + "WHERE library_id=:library AND featured AND book_id<>:book", params, Long.class);
            if (count >= 12) throw error(HttpStatus.BAD_REQUEST, "首页精选最多 12 本，请先取消部分精选");
        }
        String sql = "UPDATE personal_library_items SET sort_order=sort_order";
        if (move) {
            sql += ",group_id=:group";
            params.addValue("group", groupId);
        }
        if (favorite != null) {
            sql += ",favorite=:favorite,favorite_at=CASE WHEN :favorite THEN CURRENT_TIMESTAMP ELSE NULL END";
            params.addValue("favorite", favorite);
        }
        if (featured != null) {
            sql += ",featured=:featured,featured_order=CASE WHEN :featured AND NOT featured THEN "
                    + "COALESCE((SELECT MAX(featured_order)+1 FROM personal_library_items "
                    + "WHERE library_id=:library),0) ELSE featured_order END";
            params.addValue("featured", featured);
        }
        sql += " WHERE library_id=:library AND book_id=:book AND EXISTS "
                + "(SELECT 1 FROM books b WHERE b.id=:book AND " + VISIBLE + ")";
        if (jdbc.update(sql, params) == 0) throw error(HttpStatus.NOT_FOUND, "本馆书籍不存在");
    }

    @Transactional
    public void remove(User user, long id, List<Long> bookIds, Long listId) {
        owned(user, id, true);
        checkIds(bookIds, 200);
        var params = args(user, id).addValue("ids", bookIds).addValue("list", listId);
        if (listId != null) {
            collection(user, id, "lists", listId);
            jdbc.update("DELETE FROM personal_library_list_items WHERE library_id=:library "
                    + "AND list_id=:list AND book_id IN (:ids)", params);
        } else jdbc.update("DELETE FROM personal_library_items WHERE library_id=:library AND book_id IN (:ids)", params);
    }

    public List<Map<String, Object>> lists(User user, long id) {
        owned(user, id, false);
        return rows("SELECT l.id,l.name,l.description,l.recommended,l.sort_order AS \"sortOrder\","
                + "(SELECT COUNT(*) FROM personal_library_list_items li JOIN books b ON b.id=li.book_id "
                + "WHERE li.list_id=l.id AND " + VISIBLE + ") AS \"bookCount\" "
                + "FROM personal_library_lists l WHERE l.library_id=:library ORDER BY l.sort_order,l.id", args(user, id));
    }

    @Transactional
    public void collectionSave(User user, long id, String kind, Long collectionId,
            String name, String description, boolean recommended) {
        owned(user, id, true);
        String table = table(kind);
        if (collectionId != null) collection(user, id, kind, collectionId);
        String clean = name(name);
        if ("groups".equals(kind) && Set.of("全部书籍", "未分组").contains(clean)) {
            throw error(HttpStatus.BAD_REQUEST, "不能使用系统分组名称");
        }
        unique(user, collectionId == null ? 0 : collectionId, table, id, clean);
        var parameters = args(user, id).addValue("id", collectionId).addValue("name", clean)
                .addValue("description", description(description)).addValue("recommended", recommended);
        boolean list = "lists".equals(kind);
        if (list && recommended) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM personal_library_lists "
                    + "WHERE library_id=:library AND recommended AND id<>:id",
                    args(user, id).addValue("id", collectionId == null ? 0 : collectionId), Long.class);
            if (count >= 6) throw error(HttpStatus.BAD_REQUEST, "首页推荐书单最多 6 个");
        }
        if (collectionId == null) {
            jdbc.update("INSERT INTO " + table + "(library_id,name,sort_order"
                    + (list ? ",description,recommended" : "") + ") VALUES(:library,:name,"
                    + "COALESCE((SELECT MAX(sort_order)+1 FROM " + table + " WHERE library_id=:library),0)"
                    + (list ? ",:description,:recommended" : "") + ")", parameters);
        } else {
            jdbc.update("UPDATE " + table + " SET name=:name"
                    + (list ? ",description=:description,recommended=:recommended" : "")
                    + " WHERE id=:id AND library_id=:library", parameters);
        }
    }

    @Transactional
    public void collectionDelete(User user, long id, String kind, long collectionId) {
        owned(user, id, true);
        collection(user, id, kind, collectionId);
        var parameters = args(user, id).addValue("id", collectionId);
        if ("groups".equals(kind)) jdbc.update("UPDATE personal_library_items SET group_id=NULL "
                + "WHERE library_id=:library AND group_id=:id", parameters);
        jdbc.update("DELETE FROM " + table(kind) + " WHERE library_id=:library AND id=:id", parameters);
    }

    /** Move relative to a neighbour, without downloading an entire collection to the browser. */
    @Transactional
    public void order(User user, long id, String kind, long itemId, long otherId, Long listId) {
        if (kind == null) throw error(HttpStatus.BAD_REQUEST, "排序类型不能为空");
        if (itemId == otherId) return;
        if ("libraries".equals(kind)) {
            lockOwner(user);
            owned(user, itemId, true);
            owned(user, otherId, true);
        } else owned(user, id, true);
        String table;
        String key;
        String scope;
        String order = "featured".equals(kind) ? "featured_order" : "sort_order";
        var parameters = args(user, id).addValue("one", itemId).addValue("two", otherId)
                .addValue("list", listId);
        switch (kind) {
            case "libraries" -> { table = "personal_libraries"; key = "id"; scope = "user_id=:user"; }
            case "groups", "lists" -> { table = table(kind); key = "id"; scope = "library_id=:library"; }
            case "books", "featured" -> { table = "personal_library_items"; key = "book_id";
                scope = "library_id=:library" + ("featured".equals(kind) ? " AND featured" : ""); }
            case "list-books" -> { collection(user, id, "lists", listId == null ? 0 : listId);
                table = "personal_library_list_items"; key = "book_id"; scope = "library_id=:library AND list_id=:list"; }
            default -> throw error(HttpStatus.BAD_REQUEST, "排序类型无效");
        }
        var selected = rows("SELECT " + key + " AS id," + order + " AS position FROM " + table
                + " WHERE " + scope + " AND " + key + " IN (:one,:two) FOR UPDATE", parameters);
        if (selected.size() != 2) throw error(HttpStatus.BAD_REQUEST, "排序项不存在或已变化，请刷新");
        long first = ((Number) selected.get(0).get("position")).longValue();
        long second = ((Number) selected.get(1).get("position")).longValue();
        if (first == second) throw error(HttpStatus.CONFLICT, "排序已变化，请刷新后重试");
        for (var row : selected) jdbc.update("UPDATE " + table + " SET " + order + "=:position WHERE "
                        + scope + " AND " + key + "=:item",
                new MapSqlParameterSource(parameters.getValues()).addValue("item", row.get("id"))
                        .addValue("position", ((Number) row.get("position")).longValue() == first ? second : first));
    }

    private void collection(User user, long library, String kind, long id) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table(kind)
                + " WHERE id=:id AND library_id=:library", args(user, library).addValue("id", id), Long.class);
        if (count != 1) throw error(HttpStatus.NOT_FOUND, "馆内分组或书单不存在");
    }

    private String table(String kind) {
        return switch (kind) {
            case "groups" -> "personal_library_groups";
            case "lists" -> "personal_library_lists";
            default -> throw error(HttpStatus.BAD_REQUEST, "类型无效");
        };
    }

    private void lockOwner(User user) {
        jdbc.queryForList("SELECT id FROM users WHERE id=:user FOR UPDATE", args(user, 0));
    }

    private void unique(User user, long id, String table, long library, String name) {
        String scope = table.equals("personal_libraries") ? "user_id=:user" : "library_id=:library";
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table
                + " WHERE " + scope + " AND name=:name AND id<>:id",
                args(user, library).addValue("id", id).addValue("name", name), Long.class);
        if (count > 0) throw error(HttpStatus.CONFLICT, "名称已存在");
    }

    static String name(String value) {
        String result = value == null ? "" : value.strip();
        if (result.isBlank() || result.codePointCount(0, result.length()) > 50) {
            throw error(HttpStatus.BAD_REQUEST, "名称须为 1–50 个字符");
        }
        return result;
    }

    private String description(String value) {
        String result = value == null ? "" : value.strip();
        if (result.codePointCount(0, result.length()) > 500) throw error(HttpStatus.BAD_REQUEST, "简介最多 500 字");
        return result;
    }

    static String search(String value) {
        String clean = value == null ? "" : value.strip();
        if (clean.length() > 200) throw error(HttpStatus.BAD_REQUEST, "搜索内容过长");
        return "%" + clean.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    static void checkPage(int page, int size) {
        if (page < 0 || !SIZES.contains(size)) throw error(HttpStatus.BAD_REQUEST, "分页参数无效");
    }

    static void checkIds(List<Long> ids, int max) {
        if (ids == null || ids.isEmpty() || ids.size() > max || ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw error(HttpStatus.BAD_REQUEST, "请选择 1–" + max + " 个有效项目");
        }
    }

    String encode(Map<String, Object> config) {
        Map<String, Object> clean = new LinkedHashMap<>();
        if (config == null) config = Map.of();
        for (String key : List.of("template", "accent", "background", "layout", "coverSize", "cover")) {
            String value = String.valueOf(config.getOrDefault(key, switch (key) {
                case "template" -> "simple";
                case "accent" -> "site";
                case "background" -> "plain";
                case "layout" -> "grid";
                case "coverSize" -> "medium";
                default -> "landscape";
            }));
            Set<String> allowed = switch (key) {
                case "template" -> Set.of("simple", "shelf", "editorial");
                case "accent" -> Set.of("site", "forest", "blue", "brown");
                case "background" -> Set.of("plain", "texture");
                case "layout" -> Set.of("grid", "list");
                case "coverSize" -> Set.of("small", "medium", "large");
                default -> Set.of("landscape", "arches", "lines");
            };
            if (!allowed.contains(value)) throw error(HttpStatus.BAD_REQUEST, "外观选项无效");
            clean.put(key, value);
        }
        Object coverImage = config.get("coverImage");
        if (coverImage != null && !String.valueOf(coverImage).isBlank()) {
            if (!(coverImage instanceof String key) || !key.matches("[a-f0-9-]{36}")) {
                throw error(HttpStatus.BAD_REQUEST, "自定义封面标识无效");
            }
            clean.put("coverImage", coverImage);
        }
        Object moduleInput = config.getOrDefault("modules", MODULES);
        if (!(moduleInput instanceof List<?> list) || list.size() > 4
                || list.stream().anyMatch(item -> !(item instanceof String) || !MODULES.contains(item)) || list.stream().distinct().count() != list.size()) {
            throw error(HttpStatus.BAD_REQUEST, "首页模块配置无效");
        }
        clean.put("modules", moduleInput);
        for (String key : List.of("bookLimit", "listLimit", "pageSize")) {
            Object value = config.getOrDefault(key, key.equals("bookLimit") ? 8 : key.equals("listLimit") ? 4 : 24);
            Set<Integer> allowed = key.equals("bookLimit") ? Set.of(4, 8, 12)
                    : key.equals("listLimit") ? Set.of(2, 4, 6) : SIZES;
            if (!(value instanceof Number number) || !allowed.contains(number.intValue())
                    || number.doubleValue() != number.intValue()) throw error(HttpStatus.BAD_REQUEST, "显示数量无效");
            clean.put(key, ((Number) value).intValue());
        }
        for (String key : List.of("showDescription", "showCount", "showCover")) {
            Object value = config.getOrDefault(key, true);
            if (!(value instanceof Boolean)) throw error(HttpStatus.BAD_REQUEST, "展示开关无效");
            clean.put(key, value);
        }
        try { return mapper.writeValueAsString(clean); }
        catch (JsonProcessingException exception) { throw error(HttpStatus.BAD_REQUEST, "配置无法保存"); }
    }

    private void decodeConfig(Map<String, Object> row) {
        try { row.put("config", mapper.readValue(String.valueOf(row.get("config")), Map.class)); }
        catch (JsonProcessingException exception) { row.put("config", new HashMap<>()); }
        row.remove("user_id");
    }

    private List<Map<String, Object>> rows(String sql, MapSqlParameterSource parameters) {
        var result = jdbc.queryForList(sql, parameters);
        result.forEach(row -> row.replaceAll((key, value) -> value instanceof Timestamp timestamp
                ? timestamp.toLocalDateTime().toString() : value));
        return result;
    }

    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
