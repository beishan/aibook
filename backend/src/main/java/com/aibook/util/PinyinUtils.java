package com.aibook.util;

import com.taptap.pinyin.PinyinPlus;

import java.util.Locale;

/**
 * 书籍拼音搜索索引工具。
 *
 * <p>基于 pinyin-plus（TapTap，Apache-2.0）将书名/作者转换为拼音检索串，
 * 同时保存无空格全拼、分词全拼和首字母缩写三种形态，
 * 支持“sanguo”“sanguoyanyi”“sgyy”等输入命中。</p>
 */
public final class PinyinUtils {

    /** search_pinyin 列的安全截断长度，防止极端超长书名撑爆列宽。 */
    public static final int MAX_INDEX_LENGTH = 2000;

    private PinyinUtils() {
    }

    /**
     * 生成拼音检索串。
     *
     * <p>示例：书名“三国演义”、作者“罗贯中” 生成
     * “sanguoyanyi san guo yan yi sgyy luoguanzhong luo guan zhong lgz”。</p>
     *
     * @param title  书名，可为 null
     * @param author 作者，可为 null
     * @return 小写拼音检索串；书名作者均为空时返回空串
     */
    public static String buildSearchIndex(String title, String author) {
        StringBuilder sb = new StringBuilder();
        appendToken(sb, compactPinyin(title));
        appendToken(sb, spacedPinyin(title));
        appendToken(sb, initials(title));
        appendToken(sb, compactPinyin(author));
        appendToken(sb, spacedPinyin(author));
        appendToken(sb, initials(author));
        String result = sb.toString().trim().toLowerCase(Locale.ROOT);
        if (result.length() > MAX_INDEX_LENGTH) {
            return result.substring(0, MAX_INDEX_LENGTH);
        }
        return result;
    }

    private static void appendToken(StringBuilder sb, String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(' ');
        }
        sb.append(token);
    }

    private static boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    /** 无空格全拼，例如“三国演义”→“sanguoyanyi”；非汉字字符原样保留。 */
    static String compactPinyin(String text) {
        if (!hasText(text)) {
            return "";
        }
        return safeTo(text).replace(" ", "");
    }

    /** 分词全拼，例如“三国演义”→“san guo yan yi”。 */
    static String spacedPinyin(String text) {
        if (!hasText(text)) {
            return "";
        }
        return safeTo(text).trim();
    }

    /** 首字母缩写，例如“三国演义”→“sgyy”；非汉字按原字符小写保留。 */
    static String initials(String text) {
        if (!hasText(text)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String token : safeTo(text).split(" ")) {
            if (token.isEmpty()) {
                continue;
            }
            char first = token.charAt(0);
            // to() 对非汉字字符原样输出，仅取汉字拼音的首字母。
            if (isAsciiLetterOrDigit(first)) {
                sb.append(Character.toLowerCase(first));
            }
        }
        return sb.toString();
    }

    /** pinyin-plus 对异常输入可能抛错，统一兜底避免影响书籍保存主流程。 */
    private static String safeTo(String text) {
        try {
            String result = PinyinPlus.to(text);
            return result == null ? text : result;
        } catch (RuntimeException e) {
            return text;
        }
    }

    private static boolean isAsciiLetterOrDigit(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    /**
     * 判断关键词是否为纯 ASCII（拉丁字母、数字等），用于决定是否参与拼音列匹配。
     */
    public static boolean isAsciiKeyword(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return false;
        }
        for (int i = 0; i < keyword.length(); i++) {
            if (keyword.charAt(i) >= 128) {
                return false;
            }
        }
        return true;
    }

    /**
     * 拼音匹配用的关键词归一：去空格并小写，便于匹配无空格全拼形态。
     */
    public static String normalizePinyinKeyword(String keyword) {
        if (keyword == null) {
            return "";
        }
        return keyword.replace(" ", "").toLowerCase(Locale.ROOT);
    }
}
