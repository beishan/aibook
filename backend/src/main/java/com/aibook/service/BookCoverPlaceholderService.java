package com.aibook.service;

import com.aibook.model.entity.User;
import com.aibook.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookCoverPlaceholderService {
    public record Settings(String mode, Integer length, String text) {}
    private static final Settings DEFAULTS = new Settings("PREFIX", 2, "书");
    private final UserRepository users;
    private final ObjectMapper mapper;

    @Transactional(readOnly = true)
    public Settings get(User user) {
        try {
            if (user.getBookCoverPlaceholderSettings() != null) {
                return validate(mapper.readValue(user.getBookCoverPlaceholderSettings(), Settings.class));
            }
        } catch (Exception ignored) {
            // Unset or obsolete preferences use the default placeholder.
        }
        return DEFAULTS;
    }

    @Transactional
    public Settings save(User user, Settings request) {
        Settings settings = validate(request);
        try {
            user.setBookCoverPlaceholderSettings(mapper.writeValueAsString(settings));
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException("无法保存无图文字设置", error);
        }
        users.save(user);
        return settings;
    }

    private Settings validate(Settings value) {
        if (value == null || value.mode() == null
                || !Set.of("PREFIX", "FULL", "CUSTOM").contains(value.mode())
                || value.length() == null || value.length() < 1 || value.length() > 20
                || value.text() == null || value.text().isBlank()
                || value.text().strip().codePointCount(0, value.text().strip().length()) > 40
                || value.text().codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("请选择有效显示方式，字数为 1–20，固定文字为 1–40 个字符");
        }
        return new Settings(value.mode(), value.length(), value.text().strip());
    }
}
