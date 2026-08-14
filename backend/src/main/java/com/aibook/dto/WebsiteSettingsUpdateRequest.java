package com.aibook.dto;

import java.util.Map;

public record WebsiteSettingsUpdateRequest(
        String siteName,
        String browserTitle,
        String loginDescription,
        Boolean registrationEnabled,
        Map<String, String> loginStyles) {}
