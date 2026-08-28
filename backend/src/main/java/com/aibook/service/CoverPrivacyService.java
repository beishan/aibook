package com.aibook.service;

import com.aibook.dto.CoverPrivacyScopeDTO;
import com.aibook.model.entity.User;
import com.aibook.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 按账号保存书库与随机封面素材库的图片隐藏偏好。 */
@Service
@RequiredArgsConstructor
public class CoverPrivacyService {

    private static final TypeReference<Map<Long, Boolean>> OVERRIDES_TYPE =
            new TypeReference<>() {};

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public CoverPrivacyScopeDTO getBookCoverSettings(User user) {
        return toScope(user.getAllBookCoversHidden(), user.getBookCoverVisibilityOverrides());
    }

    @Transactional
    public CoverPrivacyScopeDTO updateBookCoverSettings(
            User user, CoverPrivacyScopeDTO request) {
        Map<Long, Boolean> overrides = normalize(request.overrides());
        user.setAllBookCoversHidden(request.allHidden());
        user.setBookCoverVisibilityOverrides(write(overrides));
        return getBookCoverSettings(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public CoverPrivacyScopeDTO getRandomCoverSettings(User user) {
        return toScope(user.getAllRandomCoversHidden(), user.getRandomCoverVisibilityOverrides());
    }

    @Transactional
    public CoverPrivacyScopeDTO updateRandomCoverSettings(
            User user, CoverPrivacyScopeDTO request) {
        Map<Long, Boolean> overrides = normalize(request.overrides());
        user.setAllRandomCoversHidden(request.allHidden());
        user.setRandomCoverVisibilityOverrides(write(overrides));
        return getRandomCoverSettings(userRepository.save(user));
    }

    private CoverPrivacyScopeDTO toScope(Boolean allHidden, String overridesJson) {
        boolean initialized = allHidden != null || overridesJson != null;
        return new CoverPrivacyScopeDTO(
                initialized,
                Boolean.TRUE.equals(allHidden),
                read(overridesJson));
    }

    private Map<Long, Boolean> read(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return normalize(objectMapper.readValue(value, OVERRIDES_TYPE));
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String write(Map<Long, Boolean> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("封面隐藏设置保存失败", exception);
        }
    }

    private Map<Long, Boolean> normalize(Map<Long, Boolean> value) {
        if (value == null || value.isEmpty()) return Map.of();
        Map<Long, Boolean> normalized = new LinkedHashMap<>();
        value.forEach((id, hidden) -> {
            if (id != null && id > 0 && hidden != null) normalized.put(id, hidden);
        });
        return normalized;
    }
}
