package com.aibook.util;

import com.aibook.model.entity.CrawlerBook;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
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

    public record MatchDetail(String type, String field, String value, String evidence) { }

    /** Capture every match using the same literal matching rules as automatic enforcement. */
    public static List<MatchDetail> matchDetails(Configuration config, CrawlerBook book) {
        if (!config.enabled() || book == null) return List.of();
        List<MatchDetail> matches = new ArrayList<>();
        for (String keyword : config.keywords()) {
            addKeywordMatch(matches, "书名", book.getBookName(), keyword);
            addKeywordMatch(matches, "作者", book.getAuthor(), keyword);
            addKeywordMatch(matches, "简介", book.getDescription(), keyword);
        }
        List<String> tags = book.getTags() == null ? List.of()
                : Arrays.stream(book.getTags().split("[\\r\\n,，、]+"))
                        .map(String::trim).filter(tag -> !tag.isEmpty()).distinct().toList();
        for (String configured : config.tags()) {
            for (String actual : tags) {
                if (configured.equalsIgnoreCase(actual)) {
                    matches.add(new MatchDetail("TAG", "标签", configured, actual));
                }
            }
        }
        return List.copyOf(matches);
    }

    private static void addKeywordMatch(List<MatchDetail> matches, String field,
            String text, String keyword) {
        if (contains(text, keyword)) {
            matches.add(new MatchDetail("KEYWORD", field, keyword, excerpt(text, keyword)));
        }
    }

    private static String excerpt(String text, String keyword) {
        String foldedKeyword = keyword.toLowerCase(Locale.ROOT);
        String foldedText = text.toLowerCase(Locale.ROOT);
        int foldedPosition = foldedText.indexOf(foldedKeyword);
        boolean sameLength = text.length() == foldedText.length();
        int position = sameLength ? foldedPosition : originalOffset(text, foldedPosition);
        int matchEnd = sameLength ? foldedPosition + foldedKeyword.length()
                : originalOffset(text, foldedPosition + foldedKeyword.length());
        int start = Math.max(0, position - 40);
        int end = Math.min(text.length(), matchEnd + 40);
        // Keep supplementary characters intact when a context boundary lands inside a pair.
        if (start > 0 && Character.isLowSurrogate(text.charAt(start))) start--;
        if (end < text.length() && end > 0 && Character.isLowSurrogate(text.charAt(end))) end++;
        return (start > 0 ? "…" : "") + text.substring(start, end) + (end < text.length() ? "…" : "");
    }

    private static int originalOffset(String text, int foldedOffset) {
        int offset = 0;
        int consumed = 0;
        while (offset < text.length() && consumed < foldedOffset) {
            int codePoint = text.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            int width = character.toLowerCase(Locale.ROOT).length();
            if (consumed + width > foldedOffset) return offset;
            consumed += width;
            offset += Character.charCount(codePoint);
        }
        return offset;
    }

    public static String detailsJson(List<MatchDetail> details) {
        try {
            return MAPPER.writeValueAsString(details);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("保存黑名单命中详情失败", exception);
        }
    }

    public static List<MatchDetail> decodeDetails(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return List.of(MAPPER.readValue(json, MatchDetail[].class));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("黑名单命中详情损坏，请重新筛选", exception);
        }
    }

    private static boolean contains(String text, String keyword) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }
}
