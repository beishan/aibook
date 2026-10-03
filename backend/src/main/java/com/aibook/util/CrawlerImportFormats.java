package com.aibook.util;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class CrawlerImportFormats {
    private CrawlerImportFormats() { }

    public static List<String> normalize(Collection<String> requested) {
        if (requested == null || requested.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请至少选择一种入库格式");
        }
        Set<String> result = new LinkedHashSet<>();
        for (String value : requested) {
            String format = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
            if (!Set.of("STRUCTURED", "TXT", "EPUB").contains(format)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持结构化章节、TXT 或 EPUB 入库");
            }
            result.add(format);
        }
        return List.copyOf(result);
    }

    public static List<String> decode(String stored) {
        return normalize(stored == null || stored.isBlank()
                ? List.of("STRUCTURED") : Arrays.asList(stored.split(",")));
    }
}
