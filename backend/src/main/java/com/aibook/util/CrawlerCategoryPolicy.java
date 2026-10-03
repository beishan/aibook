package com.aibook.util;

import java.text.Normalizer;
import java.util.Locale;

public final class CrawlerCategoryPolicy {
    private CrawlerCategoryPolicy() { }

    public static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("[\\p{Z}\\s《》〈〉「」『』]", "")
                .toLowerCase(Locale.ROOT);
    }

    public static boolean isBookTitle(String title, String category) {
        String normalizedCategory = normalize(category);
        return !normalizedCategory.isEmpty() && normalizedCategory.equals(normalize(title));
    }

    public static String sanitize(String title, String category) {
        return isBookTitle(title, category) ? null : category;
    }
}
