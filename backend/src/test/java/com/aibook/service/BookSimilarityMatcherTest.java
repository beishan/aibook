package com.aibook.service;

import com.aibook.dto.SimilarBookDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookSimilarityMatcherTest {
    @Test
    void matchesTitlesAcrossFormatsAuthorsAndVersionSuffixes() {
        var books = List.of(
                book(1, "《三体》 精校版", "刘慈欣", "epub"),
                book(2, "三体.pdf", "未知作者", "pdf"),
                book(3, "三体", "另一位作者", "txt"),
                book(4, "Ｓｐｒｉｎｇ Boot 入门", null, "epub"),
                book(5, "spring-boot入门（电子版）", null, "pdf"));

        var groups = BookSimilarityMatcher.scan(books, ignored -> {});

        assertEquals(2, groups.size());
        assertEquals(List.of(1L, 2L, 3L), groups.getFirst().books().stream().map(SimilarBookDTO::id).toList());
        assertEquals(100, groups.getFirst().similarity());
        assertEquals(List.of(4L, 5L), groups.getLast().books().stream().map(SimilarBookDTO::id).toList());
    }

    @Test
    void fuzzyMatchesCanIncludeAnErrorInTheFirstCharacter() {
        var groups = BookSimilarityMatcher.scan(List.of(
                book(1, "深入理解Java虚拟机", null, "epub"),
                book(2, "探入理解Java虚拟机", null, "pdf")), ignored -> {});

        assertEquals(1, groups.size());
        assertTrue(groups.getFirst().similarity() >= 80);
        assertTrue(groups.getFirst().similarity() < 100);
    }

    @Test
    void doesNotGroupDifferentVolumesNumbersOrShortSimilarTitles() {
        var groups = BookSimilarityMatcher.scan(List.of(
                book(1, "Java编程教程第一卷", null, "epub"),
                book(2, "Java编程教程第二卷", null, "epub"),
                book(3, "Java编程教程上册", null, "epub"),
                book(4, "Java编程教程下册", null, "epub"),
                book(5, "编程指南2024", null, "epub"),
                book(6, "编程指南2025", null, "epub"),
                book(7, "三体", null, "epub"),
                book(8, "三休", null, "epub"),
                book(9, "  ", null, "epub"),
                book(10, null, null, "epub")), ignored -> {});

        assertTrue(groups.isEmpty());
    }

    @Test
    void groupsConnectedCandidatesOnceAndReportsActualTotalProgress() {
        AtomicInteger progress = new AtomicInteger();
        var books = List.of(
                book(1, "abcdefghij", null, "epub"),
                book(2, "abXXefghij", null, "epub"),
                book(3, "abXXefghYY", null, "epub"),
                book(4, "", null, "epub"));

        var groups = BookSimilarityMatcher.scan(books, progress::set);

        assertEquals(1, groups.size());
        assertEquals(3, groups.getFirst().books().size());
        assertEquals(80, groups.getFirst().similarity());
        assertEquals(4, progress.get());
    }

    @Test
    void indexedMatchingHasTheSameConnectedGroupsAsAnExhaustiveComparison() {
        // 长短不同、首字不同，以及阈值边界，验证二元字符索引不漏掉合格候选。
        List<SimilarBookDTO> books = new ArrayList<>();
        String[] titles = {"abcde", "xbcde", "abcdef", "abXdef", "abcxefg", "abcdefghi",
                "abcdXfghi", "中文书名测试", "中文书名测式", "别的书名", "别的书铭",
                "aaaaa", "aaaab", "aaaabb", "baaaab", "bbbbba", "bbbbb"};
        for (int index = 0; index < titles.length; index++) {
            books.add(book(index + 1, titles[index], null, "txt"));
        }
        int[] parents = java.util.stream.IntStream.range(0, titles.length).toArray();
        for (int left = 0; left < titles.length; left++) {
            for (int right = left + 1; right < titles.length; right++) {
                if (BookTitleSimilarity.score(titles[left], titles[right]) + 1e-9 >= 0.8) {
                    parents[root(parents, left)] = root(parents, right);
                }
            }
        }
        Set<Set<Long>> expected = java.util.stream.IntStream.range(0, titles.length).boxed()
                .collect(Collectors.groupingBy(index -> root(parents, index))).values().stream()
                .filter(group -> group.size() > 1)
                .map(group -> group.stream().map(index -> index + 1L).collect(Collectors.toSet()))
                .collect(Collectors.toSet());
        Set<Set<Long>> actual = BookSimilarityMatcher.scan(books, ignored -> {}).stream()
                .map(group -> group.books().stream().map(SimilarBookDTO::id).collect(Collectors.toSet()))
                .collect(Collectors.toSet());

        assertEquals(expected, actual);
    }

    private int root(int[] parents, int index) {
        while (parents[index] != index) {
            index = parents[index];
        }
        return index;
    }

    private SimilarBookDTO book(long id, String title, String author, String format) {
        return new SimilarBookDTO(id, title, author, format);
    }
}
