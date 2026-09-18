package com.aibook.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 拼音检索串生成工具测试。
 */
class PinyinUtilsTest {

    @Test
    void buildSearchIndexContainsFullCompactAndInitials() {
        String index = PinyinUtils.buildSearchIndex("三国演义", "罗贯中");
        assertTrue(index.contains("sanguoyanyi"), "应包含无空格全拼: " + index);
        assertTrue(index.contains("san guo yan yi"), "应包含分词全拼: " + index);
        assertTrue(index.contains("sgyy"), "应包含书名首字母: " + index);
        assertTrue(index.contains("luoguanzhong"), "应包含作者无空格全拼: " + index);
        assertTrue(index.contains("lgz"), "应包含作者首字母: " + index);
    }

    @Test
    void buildSearchIndexHandlesNullAndBlank() {
        assertEquals("", PinyinUtils.buildSearchIndex(null, null));
        assertEquals("", PinyinUtils.buildSearchIndex("  ", ""));
        // 作者为空时仅生成书名部分。
        String onlyTitle = PinyinUtils.buildSearchIndex("活着", null);
        assertTrue(onlyTitle.contains("huozhe"));
        assertFalse(onlyTitle.contains("null"));
    }

    @Test
    void buildSearchIndexKeepsAsciiTextSearchable() {
        String index = PinyinUtils.buildSearchIndex("JavaScript 指南", null);
        assertTrue(index.contains("javascript"), "英文应可检索: " + index);
        assertTrue(index.contains("zhinan") || index.contains("zhi nan"),
                "汉字部分应转拼音: " + index);
    }

    @Test
    void buildSearchIndexTruncatesExtremeLength() {
        String longTitle = "超".repeat(5000);
        String index = PinyinUtils.buildSearchIndex(longTitle, null);
        assertTrue(index.length() <= PinyinUtils.MAX_INDEX_LENGTH);
    }

    @Test
    void isAsciiKeywordDetectsLatinOnly() {
        assertTrue(PinyinUtils.isAsciiKeyword("sanguo"));
        assertTrue(PinyinUtils.isAsciiKeyword("SGYY"));
        assertTrue(PinyinUtils.isAsciiKeyword("docker 3"));
        assertFalse(PinyinUtils.isAsciiKeyword("三国"));
        assertFalse(PinyinUtils.isAsciiKeyword("三国sanguo"));
        assertFalse(PinyinUtils.isAsciiKeyword(""));
        assertFalse(PinyinUtils.isAsciiKeyword(null));
    }

    @Test
    void normalizePinyinKeywordRemovesSpacesAndLowercases() {
        assertEquals("sanguoyanyi", PinyinUtils.normalizePinyinKeyword("San Guo YanYi"));
        assertEquals("", PinyinUtils.normalizePinyinKeyword(null));
        assertEquals("abc", PinyinUtils.normalizePinyinKeyword("  a b c "));
    }
}
