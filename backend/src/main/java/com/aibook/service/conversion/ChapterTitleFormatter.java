package com.aibook.service.conversion;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 将识别到的 TXT 章节标题按用户规则清理并统一格式。 */
public final class ChapterTitleFormatter {
    public static final String ORIGINAL_FORMAT = "{original}";
    public static final String STANDARD_FORMAT = "第{number}章 {title}";

    private static final Pattern CHAPTER_TITLE = Pattern.compile(
            "^(?:第)?([0-9零〇一二两三四五六七八九十百千万亿]+)(?:章|节|回|卷)\\s*[：:、.．\\-—]?\\s*(.*)$");
    private static final Map<Character, Integer> DIGITS = Map.ofEntries(
            Map.entry('零', 0), Map.entry('〇', 0), Map.entry('一', 1), Map.entry('二', 2),
            Map.entry('两', 2), Map.entry('三', 3), Map.entry('四', 4), Map.entry('五', 5),
            Map.entry('六', 6), Map.entry('七', 7), Map.entry('八', 8), Map.entry('九', 9));

    private ChapterTitleFormatter() {
    }

    public static String format(String sourceTitle, String removePattern, String outputFormat) {
        String original = Objects.toString(sourceTitle, "").trim();
        String cleaned = original;
        if (removePattern != null && !removePattern.isBlank()) {
            cleaned = Pattern.compile(removePattern).matcher(cleaned).replaceAll("").trim();
        }

        String template = outputFormat == null || outputFormat.isBlank()
                ? ORIGINAL_FORMAT : outputFormat.trim();
        Matcher matcher = CHAPTER_TITLE.matcher(cleaned);
        String number = "";
        String title = cleaned;
        if (matcher.matches()) {
            number = normalizeNumber(matcher.group(1));
            title = matcher.group(2).trim();
        } else if (template.contains("{number}")) {
            return cleaned;
        }
        return template.replace("{number}", number)
                .replace("{title}", title)
                .replace("{original}", cleaned)
                .replaceAll("\\s+", " ")
                .trim();
    }

    static String normalizeNumber(String value) {
        if (value.chars().allMatch(Character::isDigit)) {
            return value;
        }
        if (value.chars().allMatch(current -> DIGITS.containsKey((char) current))) {
            StringBuilder digits = new StringBuilder();
            value.chars().forEach(current -> digits.append(DIGITS.get((char) current)));
            return digits.toString();
        }
        long total = 0;
        long section = 0;
        long digit = 0;
        for (char current : value.toCharArray()) {
            Integer mapped = DIGITS.get(current);
            if (mapped != null) {
                digit = mapped;
                continue;
            }
            long unit = switch (current) {
                case '十' -> 10;
                case '百' -> 100;
                case '千' -> 1_000;
                case '万' -> 10_000;
                case '亿' -> 100_000_000;
                default -> 0;
            };
            if (unit < 10_000) {
                section += (digit == 0 ? 1 : digit) * unit;
            } else {
                section += digit;
                total += (section == 0 ? 1 : section) * unit;
                section = 0;
            }
            digit = 0;
        }
        return Long.toString(total + section + digit);
    }

    public static String stripSourceTitle(String chapterText, String sourceTitle) {
        if (chapterText == null || chapterText.isEmpty() || sourceTitle == null || sourceTitle.isBlank()) {
            return Objects.toString(chapterText, "");
        }
        String normalized = chapterText.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            if (lines[index].isBlank()) {
                continue;
            }
            if (lines[index].trim().equals(sourceTitle.trim())) {
                lines[index] = "";
            }
            break;
        }
        return String.join("\n", lines).replaceFirst("^\\s*\\n", "");
    }
}
