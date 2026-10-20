package com.aibook.service.crawler;

import static org.assertj.core.api.Assertions.*;

import com.aibook.dto.crawler.CrawlerAutoImportDtos.*;
import com.aibook.model.entity.CrawlerBook;
import com.aibook.util.CrawlerAutoImportRules;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.server.ResponseStatusException;

class CrawlerAutoImportRulesTest {
    @Test
    void combinesRulesWithOrAndExpressionsWithAndUsingTheRequestedExamples() {
        Configuration configuration = configuration(List.of(
                new Rule("神话", List.of(values(ExpressionType.KEYWORD, "神话", "传说"), count(Comparison.GT, 10))),
                new Rule("标签", List.of(values(ExpressionType.TAG, "aaa"), count(Comparison.GT, 20)))));
        CrawlerBook book = CrawlerBook.builder().bookName("神话故事").chapterCount(11).build();
        assertThat(CrawlerAutoImportRules.matches(configuration, book)).isTrue();
        book.setChapterCount(10);
        assertThat(CrawlerAutoImportRules.matches(configuration, book)).isFalse();
        book.setBookName("普通书籍");
        book.setTags("aaa");
        book.setChapterCount(21);
        assertThat(CrawlerAutoImportRules.matches(configuration, book)).isTrue();
        book.setChapterCount(20);
        assertThat(CrawlerAutoImportRules.matches(configuration, book)).isFalse();
        book.setTags("其他标签");
        book.setChapterCount(100);
        assertThat(CrawlerAutoImportRules.matches(configuration, book)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"GT,9,false", "GT,10,false", "GT,11,true", "GTE,9,false", "GTE,10,true",
            "EQ,10,true", "EQ,11,false", "NE,10,false", "NE,11,true", "LT,9,true",
            "LT,10,false", "LTE,10,true", "LTE,11,false"})
    void comparesChapterCountBoundaries(Comparison comparison, int actual, boolean expected) {
        Configuration configuration = configuration(List.of(new Rule("章节数", List.of(count(comparison, 10)))));
        assertThat(CrawlerAutoImportRules.matches(configuration,
                CrawlerBook.builder().chapterCount(actual).build())).isEqualTo(expected);
    }

    @Test
    void keywordsMatchAnySupportedFieldAndTreatSpecialCharactersAsPlainText() {
        Configuration configuration = configuration(List.of(new Rule("关键词", List.of(
                values(ExpressionType.KEYWORD, "无匹配", "MYTH", "[.*]")))));
        CrawlerBook book = CrawlerBook.builder().bookName(null).author(null).description(null).build();
        assertThat(CrawlerAutoImportRules.matches(configuration, book)).isFalse();
        book.setAuthor("myth writer");
        assertThat(CrawlerAutoImportRules.matches(configuration, book)).isTrue();
        book.setAuthor(null);
        book.setDescription("a Myth story");
        assertThat(CrawlerAutoImportRules.matches(configuration, book)).isTrue();
        book.setDescription("任意文本");
        assertThat(CrawlerAutoImportRules.matches(configuration, book)).isFalse();
        book.setBookName("包含 [.*] 的书名");
        assertThat(CrawlerAutoImportRules.matches(configuration, book)).isTrue();
    }

    @Test
    void tagsMatchAnyExactTagAndUseExistingTagSeparators() {
        Configuration configuration = configuration(List.of(new Rule("标签", List.of(
                values(ExpressionType.TAG, "aaa", "神话")))));
        CrawlerBook book = CrawlerBook.builder().tags("aaaa,神话故事").build();
        assertThat(CrawlerAutoImportRules.matches(configuration, book)).isFalse();
        book.setTags("其他， aaa\n玄幻、传说");
        assertThat(CrawlerAutoImportRules.matches(configuration, book)).isTrue();
        book.setTags(null);
        assertThat(CrawlerAutoImportRules.matches(configuration, book)).isFalse();
    }

    @Test
    void disabledRulesUnknownCountsAndExcludedBooksCannotMatch() {
        Rule rule = new Rule("数量", List.of(count(Comparison.GTE, 0)));
        CrawlerBook book = CrawlerBook.builder().chapterCount(1).build();
        assertThat(CrawlerAutoImportRules.matches(new Configuration(false, List.of("TXT"), List.of(rule)), book)).isFalse();
        Configuration enabled = configuration(List.of(rule));
        book.setChapterCount(null);
        assertThat(CrawlerAutoImportRules.matches(enabled, book)).isFalse();
        book.setChapterCount(1);
        book.setDiscoveryStatus(CrawlerBook.DiscoveryStatus.IGNORED);
        assertThat(CrawlerAutoImportRules.matches(enabled, book)).isFalse();
        book.setDiscoveryStatus(CrawlerBook.DiscoveryStatus.BLACKLISTED);
        assertThat(CrawlerAutoImportRules.matches(enabled, book)).isFalse();
    }

    @Test
    void normalizationRoundTripsAndRejectsIncompleteOrMalformedRules() {
        Configuration original = new Configuration(true, List.of("TXT", "TXT"), List.of(
                new Rule(" 名称 ", List.of(values(ExpressionType.KEYWORD, " 神话 ", "神话")))));
        Configuration normalized = CrawlerAutoImportRules.normalize(original);
        assertThat(normalized.formats()).containsExactly("TXT");
        assertThat(normalized.rules().getFirst().name()).isEqualTo("名称");
        assertThat(normalized.rules().getFirst().expressions().getFirst().values()).containsExactly("神话");
        assertThat(CrawlerAutoImportRules.decode(CrawlerAutoImportRules.encode(normalized))).isEqualTo(normalized);
        assertThat(CrawlerAutoImportRules.decode(null)).isEqualTo(CrawlerAutoImportRules.defaults());
        assertThatThrownBy(() -> configuration(List.of())).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> configuration(List.of(new Rule("空", List.of()))))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> configuration(List.of(new Rule("空值", List.of(values(ExpressionType.TAG, " "))))))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> configuration(List.of(new Rule("缺少比较", List.of(count(null, 1))))))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> CrawlerAutoImportRules.decode("{broken"))
                .isInstanceOf(IllegalStateException.class);
    }

    private Configuration configuration(List<Rule> rules) {
        return CrawlerAutoImportRules.normalize(new Configuration(true, List.of("STRUCTURED"), rules));
    }

    private Expression values(ExpressionType type, String... values) {
        return new Expression(type, List.of(values), null, null);
    }

    private Expression count(Comparison comparison, int value) {
        return new Expression(ExpressionType.CHAPTER_COUNT, List.of(), comparison, value);
    }
}
