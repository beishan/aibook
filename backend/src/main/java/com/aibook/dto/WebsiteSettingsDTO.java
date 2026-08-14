package com.aibook.dto;

import java.util.Map;

public record WebsiteSettingsDTO(
        String siteName,
        String browserTitle,
        String loginDescription,
        boolean registrationEnabled,
        Map<String, String> loginStyles,
        boolean hasLoginIcon,
        String loginIconUrl,
        long loginIconVersion) {}
