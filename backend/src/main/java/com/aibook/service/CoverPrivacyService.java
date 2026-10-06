package com.aibook.service;

import com.aibook.dto.CoverPrivacyScopeDTO;
import com.aibook.dto.BookCoverImageSizesDTO;
import com.aibook.model.entity.User;
import com.aibook.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 按账号保存书库与随机封面素材库的图片隐藏偏好。 */
@Service
@RequiredArgsConstructor
public class CoverPrivacyService {

    private static final TypeReference<Map<Long, Boolean>> OVERRIDES_TYPE =
            new TypeReference<>() {};
    private static final TypeReference<Map<String, String>> IMAGE_SIZES_TYPE =
            new TypeReference<>() {};
    private static final BookCoverImageSizesDTO DEFAULT_IMAGE_SIZES =
            new BookCoverImageSizesDTO("320", "96", "320");
    private static final Set<String> ALLOWED_IMAGE_SIZES = Set.of(
            "96", "160", "320", "640", "original");

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
    public BookCoverImageSizesDTO getBookCoverImageSizes(User user) {
        String stored = user.getBookCoverImageSizes();
        if (stored == null || stored.isBlank()) return DEFAULT_IMAGE_SIZES;
        try {
            Map<String, String> values = objectMapper.readValue(stored, IMAGE_SIZES_TYPE);
            return new BookCoverImageSizesDTO(
                    allowedSize(values.get("card"), DEFAULT_IMAGE_SIZES.card()),
                    allowedSize(values.get("list"), DEFAULT_IMAGE_SIZES.list()),
                    allowedSize(values.get("detail"), DEFAULT_IMAGE_SIZES.detail()));
        } catch (Exception ignored) {
            return DEFAULT_IMAGE_SIZES;
        }
    }

    @Transactional
    public BookCoverImageSizesDTO updateBookCoverImageSizes(
            User user, BookCoverImageSizesDTO request) {
        if (request == null
                || !isAllowedSize(request.card())
                || !isAllowedSize(request.list())
                || !isAllowedSize(request.detail())) {
            throw new IllegalArgumentException("请选择有效的封面图片尺寸");
        }
        user.setBookCoverImageSizes(writeImageSizes(Map.of(
                "card", request.card(), "list", request.list(), "detail", request.detail())));
        return getBookCoverImageSizes(userRepository.save(user));
    }

    private String allowedSize(String value, String fallback) {
        return isAllowedSize(value) ? value : fallback;
    }

    private boolean isAllowedSize(String value) {
        return value != null && ALLOWED_IMAGE_SIZES.contains(value);
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

    private String writeImageSizes(Map<String, String> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("封面图片尺寸设置保存失败", exception);
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
