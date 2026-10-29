package com.aibook.service;

import com.aibook.model.entity.User;
import com.aibook.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookCardBadgeSettingsService {
    public record Settings(Map<String, String> abbreviations, Integer transparency) {}

    private static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry("structured", "线"), Map.entry("epub", "E"), Map.entry("pdf", "P"),
            Map.entry("txt", "文"), Map.entry("mobi", "M"), Map.entry("azw3", "A"),
            Map.entry("docx", "W"), Map.entry("doc", "W"), Map.entry("html", "H"),
            Map.entry("md", "简"), Map.entry("cbz", "漫"), Map.entry("cbr", "漫"),
            Map.entry("other", "书"));

    private final UserRepository users;
    private final ObjectMapper mapper;

    @Transactional(readOnly = true)
    public Settings get(User user) {
        try {
            if (user.getBookCardBadgeSettings() != null) {
                return normalize(mapper.readValue(user.getBookCardBadgeSettings(), Settings.class));
            }
        } catch (Exception ignored) {
            // Old or damaged settings must not prevent the bookshelf from loading.
        }
        return new Settings(new LinkedHashMap<>(DEFAULTS), 30);
    }

    @Transactional
    public Settings save(User user, Settings request) {
        Settings settings = normalize(request);
        try {
            user.setBookCardBadgeSettings(mapper.writeValueAsString(settings));
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException("无法保存卡片标签设置", error);
        }
        users.save(user);
        return settings;
    }

    private Settings normalize(Settings value) {
        if (value == null || value.transparency() == null
                || value.transparency() < 0 || value.transparency() > 100) {
            throw new IllegalArgumentException("标签透明度须为 0–100");
        }
        Map<String, String> result = new LinkedHashMap<>(DEFAULTS);
        if (value.abbreviations() != null) {
            value.abbreviations().forEach((key, text) -> {
                if (!DEFAULTS.containsKey(key) || text == null || text.isBlank()
                        || text.strip().codePointCount(0, text.strip().length()) > 4
                        || text.codePoints().anyMatch(Character::isISOControl)) {
                    throw new IllegalArgumentException("类型缩写须为 1–4 个字符，且类型有效");
                }
                result.put(key, text.strip());
            });
        }
        return new Settings(result, value.transparency());
    }
}
