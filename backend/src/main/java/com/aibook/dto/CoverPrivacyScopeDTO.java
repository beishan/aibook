package com.aibook.dto;

import java.util.Map;

/** 当前用户某一类封面的隐藏设置。 */
public record CoverPrivacyScopeDTO(
        boolean initialized,
        boolean allHidden,
        Map<Long, Boolean> overrides) {}
