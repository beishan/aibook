package com.aibook.service.conversion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChapterTitleFormatterTest {
    @Test
    void removesPrefixAndConvertsChineseChapterNumber() {
        assertEquals("第255章 砸场", ChapterTitleFormatter.format(
                "正文 第两百五十五章 砸场", "^正文\\s*", "第{number}章 {title}"));
    }

    @Test
    void supportsOriginalPlaceholderAndChineseDigitSequence() {
        assertEquals("卷2024·终章", ChapterTitleFormatter.format(
                "正文 第二〇二四章 终章", "^正文\\s*", "卷{number}·{title}"));
        assertEquals("第十二章 再会", ChapterTitleFormatter.format(
                "正文 第十二章 再会", "^正文\\s*", "{original}"));
    }

    @Test
    void removesSourceHeadingFromChapterBodyAfterRename() {
        assertEquals("这是正文。", ChapterTitleFormatter.stripSourceTitle(
                "正文 第两百五十五章 砸场\n这是正文。", "正文 第两百五十五章 砸场"));
    }

    @Test
    void keepsSpecialChapterNamesWhenNumberTemplateDoesNotApply() {
        assertEquals("楔子", ChapterTitleFormatter.format(
                "正文 楔子", "^正文\\s*", "第{number}章 {title}"));
    }
}
