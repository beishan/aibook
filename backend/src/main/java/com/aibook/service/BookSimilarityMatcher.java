package com.aibook.service;

import com.aibook.dto.BookSimilarityScanDTO.Group;
import com.aibook.dto.SimilarBookDTO;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;

/** 相似关系分组；二元字符索引筛选候选，避免逐对遍历整个书库。 */
final class BookSimilarityMatcher {
    private static final double THRESHOLD = 0.80;
    private static final Pattern VOLUME = Pattern.compile(
            "[0-9]+|第[一二三四五六七八九十百千万零〇两]+[卷部册集]|[上中下][卷部册集]");

    private BookSimilarityMatcher() {}

    static List<Group> scan(List<SimilarBookDTO> books, IntConsumer progress) {
        Map<String, Node> titles = new LinkedHashMap<>();
        for (SimilarBookDTO book : books) {
            checkInterrupted();
            String title = BookTitleSimilarity.normalize(book.title());
            if (!title.isBlank()) {
                titles.computeIfAbsent(title, key -> new Node(key, titles.size())).books.add(book);
            }
        }
        List<Node> nodes = new ArrayList<>(titles.values());
        int[] parent = new int[nodes.size()];
        int[] minimumScore = new int[nodes.size()];
        for (int index = 0; index < nodes.size(); index++) {
            parent[index] = index;
            minimumScore[index] = 100;
        }
        Map<List<String>, Map<String, List<Node>>> indexes = new HashMap<>();
        int processed = books.size() - nodes.stream().mapToInt(node -> node.books.size()).sum();
        for (Node node : nodes) {
            checkInterrupted();
            Map<String, List<Node>> index = indexes.computeIfAbsent(
                    volumeIdentity(node.title), ignored -> new HashMap<>());
            Map<Node, Integer> sharedGrams = new HashMap<>();
            for (var gram : node.grams.entrySet()) {
                for (Node candidate : index.getOrDefault(gram.getKey(), List.of())) {
                    sharedGrams.merge(candidate,
                            Math.min(gram.getValue(), candidate.grams.get(gram.getKey())), Integer::sum);
                }
            }
            for (var entry : sharedGrams.entrySet()) {
                checkInterrupted();
                Node candidate = entry.getKey();
                int maximumLength = Math.max(node.title.length(), candidate.title.length());
                int maximumDistance = (int) Math.floor(maximumLength * (1 - THRESHOLD) + 1e-9);
                // 一次编辑至多改变两个二元字符；合格书名至少共享这些字符对。
                int requiredGrams = maximumLength - 1 - 2 * maximumDistance;
                if (Math.min(node.title.length(), candidate.title.length()) < 4
                        || Math.abs(node.title.length() - candidate.title.length()) > maximumDistance
                        || entry.getValue() < requiredGrams) {
                    continue;
                }
                int left = find(parent, node.index);
                int right = find(parent, candidate.index);
                if (left == right) {
                    continue;
                }
                double score = BookTitleSimilarity.score(node.title, candidate.title);
                if (score + 1e-9 >= THRESHOLD) {
                    parent[right] = left;
                    minimumScore[left] = Math.min(
                            (int) Math.round(score * 100),
                            Math.min(minimumScore[left], minimumScore[right]));
                }
            }
            for (String gram : node.grams.keySet()) {
                index.computeIfAbsent(gram, ignored -> new ArrayList<>()).add(node);
            }
            processed += node.books.size();
            progress.accept(processed);
        }
        Map<Integer, List<SimilarBookDTO>> grouped = new LinkedHashMap<>();
        for (Node node : nodes) {
            grouped.computeIfAbsent(find(parent, node.index), ignored -> new ArrayList<>())
                    .addAll(node.books);
        }
        List<Group> result = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            if (entry.getValue().size() < 2) {
                continue;
            }
            List<SimilarBookDTO> members = entry.getValue().stream()
                    .sorted(Comparator.comparing(SimilarBookDTO::id)).toList();
            result.add(new Group(
                    "similar-" + members.getFirst().id(), minimumScore[entry.getKey()], members));
        }
        progress.accept(books.size());
        return result.stream().sorted(Comparator.comparingInt(Group::similarity).reversed()
                .thenComparing(group -> group.books().getFirst().id())).toList();
    }

    private static List<String> volumeIdentity(String title) {
        return VOLUME.matcher(title).results().map(match -> match.group()).toList();
    }

    private static int find(int[] parent, int index) {
        int root = index;
        while (parent[root] != root) {
            root = parent[root];
        }
        while (parent[index] != index) {
            int next = parent[index];
            parent[index] = root;
            index = next;
        }
        return root;
    }

    private static Map<String, Integer> grams(String title) {
        Map<String, Integer> grams = new HashMap<>();
        for (int index = 0; index < title.length() - 1; index++) {
            grams.merge(title.substring(index, index + 2), 1, Integer::sum);
        }
        return grams;
    }

    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("相似书名扫描已停止");
        }
    }

    private static final class Node {
        private final String title;
        private final int index;
        private final List<SimilarBookDTO> books = new ArrayList<>();
        private final Map<String, Integer> grams;

        private Node(String title, int index) {
            this.title = title;
            this.index = index;
            this.grams = grams(title);
        }
    }
}
