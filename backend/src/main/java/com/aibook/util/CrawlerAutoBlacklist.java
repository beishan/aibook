package com.aibook.util;

import com.aibook.model.entity.CrawlerBook;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.Size;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Literal matching only: no user supplied regular expressions or executable rules. */
public final class CrawlerAutoBlacklist {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CrawlerAutoBlacklist() { }

    public record Configuration(boolean enabled,
            @Size(max = 100) List<@Size(max = 200) String> keywords,
            @Size(max = 100) List<@Size(max = 200) String> tags) { }

    public static Configuration normalize(Configuration value) {
        if (value == null) return new Configuration(false, List.of(), List.of());
        return new Configuration(value.enabled(), normalizeValues(value.keywords()),
                normalizeValues(value.tags()));
    }

    private static List<String> normalizeValues(List<String> values) {
        if (values == null) return List.of();
        if (values.size() > 100 || values.stream().anyMatch(v -> v != null && v.length() > 200)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "每种规则最多100项，每项最多200字");
        }
        return values.stream().filter(v -> v != null && !v.isBlank())
                .map(String::trim).distinct().toList();
    }

    public static Configuration decode(String json) {
        if (json == null || json.isBlank()) return normalize(null);
        try {
            return normalize(MAPPER.readValue(json, Configuration.class));
        } catch (Exception exception) {
            throw new IllegalStateException("自动黑名单配置损坏，请重新保存", exception);
        }
    }

    public static String encode(Configuration value) {
        try {
            return MAPPER.writeValueAsString(normalize(value));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("自动黑名单保存失败", exception);
        }
    }

    public static String reason(Configuration config, CrawlerBook book) {
        if (!config.enabled() || book == null) return null;
        for (String keyword : config.keywords()) {
            if (contains(book.getBookName(), keyword) || contains(book.getAuthor(), keyword)
                    || contains(book.getDescription(), keyword)) {
                return "命中自动黑名单关键字：" + keyword;
            }
        }
        List<String> tags = book.getTags() == null ? List.of()
                : Arrays.stream(book.getTags().split("[\\r\\n,，、]+"))
                        .map(String::trim).toList();
        for (String tag : config.tags()) {
            if (tags.stream().anyMatch(tag::equalsIgnoreCase)) {
                return "命中自动黑名单标签：" + tag;
            }
        }
        return null;
    }

    private static boolean contains(String text, String keyword) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }
}
