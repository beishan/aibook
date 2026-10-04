package com.aibook.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class CrawlerCategoryPolicy {
    private static final Pattern WHITESPACE = Pattern.compile("[\\p{Z}\\s]+");
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

    public static boolean isInvalidCategory(String title, String author, String category) {
        if (isBookTitle(title, category)) return true;
        String normalizedTitle = normalize(title);
        String normalizedAuthor = normalize(author);
        if (category == null || normalizedTitle.isEmpty() || normalizedAuthor.isEmpty()) {
            return false;
        }
        // 要求书名和作者之间实际存在空白，且两侧完整匹配，避免误清理正常分类。
        String candidate = Normalizer.normalize(category, Normalizer.Form.NFKC);
        var separators = WHITESPACE.matcher(candidate);
        while (separators.find()) {
            if (normalize(candidate.substring(0, separators.start())).equals(normalizedTitle)
                    && normalize(candidate.substring(separators.end())).equals(normalizedAuthor)) {
                return true;
            }
        }
        return false;
    }

    public static String sanitize(String title, String author, String category) {
        return isInvalidCategory(title, author, category) ? null : category;
    }
}
