package com.aibook.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** 书名匹配与已有版本聚合共用的编辑距离。 */
final class BookTitleSimilarity {
    private static final List<String> VERSION_SUFFIXES = List.of(
            "完整版", "精校版", "校对版", "修订版", "插图版", "典藏版",
            "电子版", "网络版", "出版版", "高清版", "epub", "pdf", "mobi",
            "azw3", "txt", "markdown", "md");

    private BookTitleSimilarity() {}

    static String normalize(String title) {
        if (title == null) {
            return "";
        }
        String normalized = Normalizer.normalize(title, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{P}]+", "");
        boolean changed;
        do {
            changed = false;
            for (String suffix : VERSION_SUFFIXES) {
                if (normalized.endsWith(suffix) && normalized.length() > suffix.length()) {
                    normalized = normalized.substring(0, normalized.length() - suffix.length());
                    changed = true;
                }
            }
        } while (changed);
        return normalized;
    }

    static double score(String left, String right) {
        int length = Math.max(left.length(), right.length());
        return length == 0 ? 1 : 1.0 - (double) distance(left, right) / length;
    }

    static int distance(String left, String right) {
        if (left.equals(right)) {
            return 0;
        }
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int column = 0; column <= right.length(); column++) {
            previous[column] = column;
        }
        for (int row = 1; row <= left.length(); row++) {
            current[0] = row;
            for (int column = 1; column <= right.length(); column++) {
                int replacementCost = left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1;
                current[column] = Math.min(
                        Math.min(current[column - 1] + 1, previous[column] + 1),
                        previous[column - 1] + replacementCost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }
}
