package com.aibook.dto.recommendation;

import java.util.List;

/** Empty selections mean unrestricted; distinct fields are combined with AND. */
public record RecommendationFilter(
        List<Long> categoryIds, boolean includeSubcategories,
        List<Long> tagIds, boolean matchAllTags, List<Long> excludedTagIds,
        List<Long> authorIds, List<Long> shelfGroupIds,
        List<String> statuses, List<String> formats, List<String> sources,
        Boolean favorite, Boolean wanted, Boolean onShelf,
        Integer addedWithinDays, Integer addedBeforeDays, Integer notReadDays,
        int count, int avoidDays, boolean strictAvoid) {
}
