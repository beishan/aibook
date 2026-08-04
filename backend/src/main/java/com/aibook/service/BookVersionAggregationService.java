package com.aibook.service;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookHighlight;
import com.aibook.model.entity.BookList;
import com.aibook.model.entity.BookScanSource;
import com.aibook.model.entity.BookVersion;
import com.aibook.model.entity.ReadingProgress;
import com.aibook.model.entity.User;
import com.aibook.repository.BookHighlightRepository;
import com.aibook.repository.BookListRepository;
import com.aibook.repository.BookRepository;
import com.aibook.repository.BookVersionRepository;
import com.aibook.repository.BookVersionIdentityProjection;
import com.aibook.repository.BookmarkRepository;
import com.aibook.repository.BookScanSourceRepository;
import com.aibook.repository.ReadingProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 将历史上作为独立书籍导入的同一本书重新聚合为多个可阅读版本。
 */
@Service
@RequiredArgsConstructor
public class BookVersionAggregationService {

    private final BookRepository bookRepository;
    private final BookVersionRepository bookVersionRepository;
    private final BookVersionService bookVersionService;
    private final ReadingProgressRepository readingProgressRepository;
    private final BookmarkRepository bookmarkRepository;
    private final BookHighlightRepository bookHighlightRepository;
    private final BookListRepository bookListRepository;
    private final BookScanSourceRepository bookScanSourceRepository;

    public RebuildPlan buildPlan(Long userId) {
        List<BookVersionIdentityProjection> identities =
                bookRepository.findVersionIdentitiesByUserId(userId);
        List<Book> lightweightBooks = identities.stream()
                .map(identity -> Book.builder()
                        .id(identity.getId())
                        .title(identity.getTitle())
                        .author(identity.getAuthor())
                        .isbn(identity.getIsbn())
                        .filePath(identity.getFilePath())
                        .format("")
                        .build())
                .toList();
        List<RebuildGroup> groups = buildGroups(lightweightBooks).stream()
                .map(group -> new RebuildGroup(
                        group.get(0).getTitle(),
                        group.stream().map(Book::getId).toList()))
                .toList();
        return new RebuildPlan(identities.size(), groups);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensurePrimaryVersion(Long bookId, User user) {
        List<Book> books = bookRepository.findByIdInAndUser(List.of(bookId), user);
        if (!books.isEmpty()) {
            bookVersionService.ensurePrimaryVersion(books.get(0));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int aggregatePair(Long primaryId, Long duplicateId, User user) {
        List<Book> books = bookRepository.findByIdInAndUser(
                List.of(primaryId, duplicateId), user);
        Map<Long, Book> byId = books.stream()
                .collect(java.util.stream.Collectors.toMap(Book::getId, book -> book));
        Book primary = byId.get(primaryId);
        Book duplicate = byId.get(duplicateId);
        if (primary == null || duplicate == null) {
            return 0;
        }

        bookVersionService.ensurePrimaryVersion(primary);
        bookVersionService.ensurePrimaryVersion(duplicate);
        List<BookVersion> duplicateVersions =
                bookVersionRepository
                        .findByBookOrderByPrimaryVersionDescCreatedAtAsc(duplicate);
        for (BookVersion version : duplicateVersions) {
            version.setBook(primary);
            version.setPrimaryVersion(false);
        }
        bookVersionRepository.saveAll(duplicateVersions);

        mergeMetadata(primary, duplicate);
        mergeReadingProgress(primary, duplicate, user);
        moveBookmarks(primary, duplicate);
        moveHighlights(primary, duplicate, user);
        replaceInBookLists(primary, duplicate, user);
        mergeScanSources(primary, duplicate);
        hideAggregatedBook(duplicate);
        bookRepository.save(primary);
        return duplicateVersions.size();
    }

    private List<List<Book>> buildGroups(List<Book> books) {
        UnionFind unionFind = new UnionFind(books.size());
        Map<String, Integer> isbnOwners = new HashMap<>();
        Map<String, List<Integer>> titleGroups = new LinkedHashMap<>();

        for (int index = 0; index < books.size(); index++) {
            Book book = books.get(index);
            String isbn = normalizeIsbn(book.getIsbn());
            if (isbn != null) {
                Integer owner = isbnOwners.putIfAbsent(isbn, index);
                if (owner != null) {
                    unionFind.union(owner, index);
                }
            }
            String title = normalizeText(book.getTitle());
            if (!title.isBlank()) {
                titleGroups.computeIfAbsent(title, ignored -> new ArrayList<>())
                        .add(index);
            }
        }

        for (List<Integer> titleGroup : titleGroups.values()) {
            Map<String, List<Integer>> byAuthor = new LinkedHashMap<>();
            List<Integer> unknownAuthors = new ArrayList<>();
            for (Integer index : titleGroup) {
                String author = normalizeAuthor(books.get(index).getAuthor());
                if (author.isBlank()) {
                    unknownAuthors.add(index);
                } else {
                    byAuthor.computeIfAbsent(author, ignored -> new ArrayList<>())
                            .add(index);
                }
            }
            byAuthor.values().forEach(indices -> unionAll(unionFind, indices));
            if (byAuthor.size() <= 1) {
                List<Integer> compatible = new ArrayList<>(unknownAuthors);
                byAuthor.values().forEach(compatible::addAll);
                unionAll(unionFind, compatible);
            } else {
                unionAll(unionFind, unknownAuthors);
            }
        }

        for (int left = 0; left < books.size(); left++) {
            for (int right = left + 1; right < books.size(); right++) {
                if (unionFind.find(left) == unionFind.find(right)) {
                    continue;
                }
                if (isSimilarBook(books.get(left), books.get(right))) {
                    unionFind.union(left, right);
                }
            }
        }

        Map<Integer, List<Book>> grouped = new LinkedHashMap<>();
        for (int index = 0; index < books.size(); index++) {
            grouped.computeIfAbsent(unionFind.find(index), ignored -> new ArrayList<>())
                    .add(books.get(index));
        }
        return new ArrayList<>(grouped.values());
    }

    private void unionAll(UnionFind unionFind, List<Integer> indices) {
        if (indices.size() < 2) {
            return;
        }
        int first = indices.get(0);
        for (int index = 1; index < indices.size(); index++) {
            unionFind.union(first, indices.get(index));
        }
    }

    private String normalizeIsbn(String isbn) {
        if (isbn == null) {
            return null;
        }
        String normalized = isbn.replaceAll("[^0-9Xx]", "").toUpperCase(Locale.ROOT);
        return normalized.length() == 10 || normalized.length() == 13
                ? normalized
                : null;
    }

    private String normalizeAuthor(String author) {
        String normalized = normalizeText(author);
        normalized = normalized
                .replaceFirst("^(作者|author)", "")
                .replaceFirst("(编著|著|作者)$", "");
        return switch (normalized) {
            case "未知", "未知作者", "unknown", "unknownauthor" -> "";
            default -> normalized;
        };
    }

    private boolean isSimilarBook(Book left, Book right) {
        String leftAuthor = normalizeAuthor(left.getAuthor());
        String rightAuthor = normalizeAuthor(right.getAuthor());
        if (!leftAuthor.isBlank()
                && !rightAuthor.isBlank()
                && !leftAuthor.equals(rightAuthor)) {
            return false;
        }

        boolean sameKnownAuthor = !leftAuthor.isBlank()
                && leftAuthor.equals(rightAuthor);
        boolean bothAuthorsUnknown = leftAuthor.isBlank() && rightAuthor.isBlank();
        String knownAuthor = leftAuthor.isBlank() ? rightAuthor : leftAuthor;
        if (!bothAuthorsUnknown && !sameKnownAuthor) {
            Book unknownAuthorBook = leftAuthor.isBlank() ? left : right;
            if (!containsAuthorInIdentity(unknownAuthorBook, knownAuthor)) {
                return false;
            }
        }

        Set<String> leftCores = identityCores(left, leftAuthor, rightAuthor);
        Set<String> rightCores = identityCores(right, leftAuthor, rightAuthor);
        for (String leftCore : leftCores) {
            for (String rightCore : rightCores) {
                if (leftCore.equals(rightCore) && leftCore.length() >= 2) {
                    return true;
                }
                int minimumLength = Math.min(leftCore.length(), rightCore.length());
                if (minimumLength < 4) {
                    continue;
                }
                double similarity = levenshteinSimilarity(leftCore, rightCore);
                double threshold = sameKnownAuthor
                        ? 0.78
                        : bothAuthorsUnknown ? 0.92 : 0.86;
                if (similarity >= threshold) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsAuthorInIdentity(Book book, String normalizedAuthor) {
        if (normalizedAuthor == null || normalizedAuthor.isBlank()) {
            return false;
        }
        return normalizeText(book.getTitle()).contains(normalizedAuthor)
                || normalizeText(fileStem(book.getFilePath())).contains(normalizedAuthor);
    }

    private Set<String> identityCores(
            Book book, String leftAuthor, String rightAuthor) {
        Set<String> cores = new LinkedHashSet<>();
        addIdentityCore(cores, book.getTitle(), leftAuthor, rightAuthor);
        addIdentityCore(cores, fileStem(book.getFilePath()), leftAuthor, rightAuthor);
        return cores;
    }

    private void addIdentityCore(
            Set<String> cores,
            String value,
            String leftAuthor,
            String rightAuthor) {
        String core = normalizeText(value);
        if (!leftAuthor.isBlank()) {
            core = core.replace(leftAuthor, "");
        }
        if (!rightAuthor.isBlank()) {
            core = core.replace(rightAuthor, "");
        }
        core = stripVersionNoise(core);
        if (!core.isBlank()) {
            cores.add(core);
        }
    }

    private String stripVersionNoise(String value) {
        String normalized = value;
        List<String> suffixes = List.of(
                "完整版", "精校版", "校对版", "修订版", "插图版",
                "典藏版", "电子版", "网络版", "出版版", "高清版",
                "epub", "pdf", "mobi", "azw3", "txt", "markdown", "md");
        boolean changed;
        do {
            changed = false;
            for (String suffix : suffixes) {
                if (normalized.endsWith(suffix)
                        && normalized.length() > suffix.length()) {
                    normalized = normalized.substring(
                            0, normalized.length() - suffix.length());
                    changed = true;
                }
            }
        } while (changed);
        return normalized;
    }

    private String fileStem(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return "";
        }
        try {
            String filename = Paths.get(filePath).getFileName().toString();
            int dot = filename.lastIndexOf('.');
            return dot > 0 ? filename.substring(0, dot) : filename;
        } catch (Exception ignored) {
            return filePath;
        }
    }

    private double levenshteinSimilarity(String left, String right) {
        if (left.equals(right)) {
            return 1;
        }
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int column = 0; column <= right.length(); column++) {
            previous[column] = column;
        }
        for (int row = 1; row <= left.length(); row++) {
            current[0] = row;
            for (int column = 1; column <= right.length(); column++) {
                int replacementCost =
                        left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1;
                current[column] = Math.min(
                        Math.min(current[column - 1] + 1, previous[column] + 1),
                        previous[column - 1] + replacementCost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        int maximumLength = Math.max(left.length(), right.length());
        return 1.0 - ((double) previous[right.length()] / maximumLength);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{P}]+", "");
    }

    private void mergeMetadata(Book primary, Book duplicate) {
        primary.setAuthor(preferValue(primary.getAuthor(), duplicate.getAuthor()));
        primary.setIsbn(preferValue(primary.getIsbn(), duplicate.getIsbn()));
        primary.setPublisher(preferValue(primary.getPublisher(), duplicate.getPublisher()));
        primary.setPublishDate(preferValue(primary.getPublishDate(), duplicate.getPublishDate()));
        primary.setDescription(preferLonger(primary.getDescription(), duplicate.getDescription()));
        primary.setCoverUrl(preferValue(primary.getCoverUrl(), duplicate.getCoverUrl()));
        primary.setLanguage(preferValue(primary.getLanguage(), duplicate.getLanguage()));
        primary.setNotes(mergeNotes(primary.getNotes(), duplicate.getNotes()));
        if (primary.getCategory() == null) {
            primary.setCategory(duplicate.getCategory());
        }
        if (primary.getTags() == null) {
            primary.setTags(new LinkedHashSet<>());
        }
        if (duplicate.getTags() != null) {
            primary.getTags().addAll(duplicate.getTags());
        }
        primary.setIsFavorite(Boolean.TRUE.equals(primary.getIsFavorite())
                || Boolean.TRUE.equals(duplicate.getIsFavorite()));
        primary.setIsWanted(Boolean.TRUE.equals(primary.getIsWanted())
                || Boolean.TRUE.equals(duplicate.getIsWanted()));
        if (duplicate.getRating() != null
                && (primary.getRating() == null
                || duplicate.getRating() > primary.getRating())) {
            primary.setRating(duplicate.getRating());
        }
        if (statusRank(duplicate.getReadingStatus())
                > statusRank(primary.getReadingStatus())) {
            primary.setReadingStatus(duplicate.getReadingStatus());
        }
    }

    private String preferValue(String current, String candidate) {
        return current == null || current.isBlank() ? candidate : current;
    }

    private String preferLonger(String current, String candidate) {
        if (current == null || current.isBlank()) {
            return candidate;
        }
        return candidate != null && candidate.length() > current.length()
                ? candidate
                : current;
    }

    private String mergeNotes(String current, String candidate) {
        if (candidate == null || candidate.isBlank() || Objects.equals(current, candidate)) {
            return current;
        }
        return current == null || current.isBlank()
                ? candidate
                : current + "\n\n" + candidate;
    }

    private int statusRank(Book.ReadingStatus status) {
        if (status == null) {
            return 0;
        }
        return switch (status) {
            case UNREADING -> 0;
            case READING -> 1;
            case FINISHED -> 2;
        };
    }

    private void mergeReadingProgress(Book primary, Book duplicate, User user) {
        ReadingProgress duplicateProgress =
                readingProgressRepository.findByUserAndBook(user, duplicate).orElse(null);
        if (duplicateProgress == null) {
            return;
        }
        ReadingProgress primaryProgress =
                readingProgressRepository.findByUserAndBook(user, primary).orElse(null);
        if (primaryProgress == null) {
            duplicateProgress.setBook(primary);
            readingProgressRepository.save(duplicateProgress);
            return;
        }
        if (isLater(duplicateProgress.getLastReadAt(), primaryProgress.getLastReadAt())) {
            primaryProgress.setCurrentChapter(duplicateProgress.getCurrentChapter());
            primaryProgress.setCurrentChapterTitle(duplicateProgress.getCurrentChapterTitle());
            primaryProgress.setChapterProgress(duplicateProgress.getChapterProgress());
            primaryProgress.setTotalProgress(duplicateProgress.getTotalProgress());
            primaryProgress.setReadingTimeSeconds(duplicateProgress.getReadingTimeSeconds());
            primaryProgress.setLastReadAt(duplicateProgress.getLastReadAt());
            readingProgressRepository.save(primaryProgress);
        }
        readingProgressRepository.delete(duplicateProgress);
    }

    private boolean isLater(LocalDateTime candidate, LocalDateTime current) {
        return candidate != null && (current == null || candidate.isAfter(current));
    }

    private void moveBookmarks(Book primary, Book duplicate) {
        var bookmarks = bookmarkRepository.findByBook(duplicate);
        bookmarks.forEach(bookmark -> bookmark.setBook(primary));
        bookmarkRepository.saveAll(bookmarks);
    }

    private void moveHighlights(Book primary, Book duplicate, User user) {
        for (BookHighlight highlight : bookHighlightRepository.findByBook(duplicate)) {
            if (bookHighlightRepository
                    .findByUserAndBookAndCfiRange(user, primary, highlight.getCfiRange())
                    .isPresent()) {
                bookHighlightRepository.delete(highlight);
            } else {
                highlight.setBook(primary);
                bookHighlightRepository.save(highlight);
            }
        }
    }

    private void replaceInBookLists(Book primary, Book duplicate, User user) {
        for (BookList bookList : bookListRepository.findByUser(user)) {
            boolean containedDuplicate = bookList.getBooks().stream()
                    .anyMatch(book -> Objects.equals(book.getId(), duplicate.getId()));
            if (!containedDuplicate) {
                continue;
            }
            bookList.getBooks().removeIf(
                    book -> Objects.equals(book.getId(), duplicate.getId()));
            boolean containedPrimary = bookList.getBooks().stream()
                    .anyMatch(book -> Objects.equals(book.getId(), primary.getId()));
            if (!containedPrimary) {
                bookList.getBooks().add(primary);
            }
            bookListRepository.save(bookList);
        }
    }

    /** 合并重复书籍时保留所有扫描目录来源，避免唯一约束冲突。 */
    private void mergeScanSources(Book primary, Book duplicate) {
        for (BookScanSource source : bookScanSourceRepository.findByBook(duplicate)) {
            if (bookScanSourceRepository.existsByBookAndScanDirectory(
                    primary, source.getScanDirectory())) {
                bookScanSourceRepository.delete(source);
            } else {
                source.setBook(primary);
                bookScanSourceRepository.save(source);
            }
        }
    }

    private void hideAggregatedBook(Book duplicate) {
        LocalDateTime now = LocalDateTime.now();
        duplicate.setDeletedAt(now);
        duplicate.setPurgedAt(now);
        duplicate.setCategory(null);
        duplicate.setTags(new LinkedHashSet<>());
        duplicate.setIsFavorite(false);
        duplicate.setIsWanted(false);
        duplicate.setNotes(null);
        bookRepository.save(duplicate);
    }

    public record RebuildPlan(int totalBooks, List<RebuildGroup> groups) {}

    public record RebuildGroup(String primaryTitle, List<Long> bookIds) {}

    private static final class UnionFind {
        private final int[] parent;

        private UnionFind(int size) {
            parent = new int[size];
            for (int index = 0; index < size; index++) {
                parent[index] = index;
            }
        }

        private int find(int value) {
            if (parent[value] != value) {
                parent[value] = find(parent[value]);
            }
            return parent[value];
        }

        private void union(int left, int right) {
            parent[find(right)] = find(left);
        }
    }
}
