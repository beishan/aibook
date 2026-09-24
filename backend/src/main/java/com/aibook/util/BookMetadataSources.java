package com.aibook.util;

import com.aibook.model.entity.Book;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

/** 元信息字段来源的 JSON 读写工具。 */
public final class BookMetadataSources {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, String>> MAP_TYPE =
            new TypeReference<>() {};

    private BookMetadataSources() {}

    public static Map<String, String> read(Book book) {
        if (book.getMetadataSources() == null || book.getMetadataSources().isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, String> sources = OBJECT_MAPPER.readValue(
                    book.getMetadataSources(), MAP_TYPE);
            return sources == null ? new LinkedHashMap<>() : sources;
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    public static void mark(Book book, String field, String source) {
        if (field == null || field.isBlank() || source == null || source.isBlank()) {
            return;
        }
        Map<String, String> sources = read(book);
        sources.put(field, source);
        write(book, sources);
    }

    public static void mark(Book book, Iterable<String> fields, String source) {
        if (fields == null) return;
        Map<String, String> sources = read(book);
        for (String field : fields) {
            if (field != null && !field.isBlank()) sources.put(field, source);
        }
        write(book, sources);
    }

    private static void write(Book book, Map<String, String> sources) {
        try {
            book.setMetadataSources(OBJECT_MAPPER.writeValueAsString(sources));
        } catch (Exception ignored) {
            // 来源记录失败时不阻断书籍元信息保存。
        }
    }
}
