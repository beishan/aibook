package com.aibook.dto.recommendation;

public record RecommendationRuleRequest(
        String name, boolean enabled, int sortOrder, RecommendationFilter filter) {
}
