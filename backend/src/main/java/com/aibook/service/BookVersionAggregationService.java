package com.aibook.service;

import com.aibook.dto.BookVersionRebuildResultDTO;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookHighlight;
import com.aibook.model.entity.BookList;
import com.aibook.model.entity.BookVersion;
import com.aibook.model.entity.ReadingProgress;
import com.aibook.model.entity.User;
import com.aibook.repository.BookHighlightRepository;
import com.aibook.repository.BookListRepository;
import com.aibook.repository.BookRepository;
import com.aibook.repository.BookVersionRepository;
import com.aibook.repository.BookmarkRepository;
import com.aibook.repository.ReadingProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

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

    @Transactional
    public BookVersionRebuildResultDTO rebuild(User user) {
        List<Book> books = bookRepository.findByUserAndDeletedAtIsNull(user).stream()
                .sorted(Comparator.comparing(
                        Book::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();
        if (books.isEmpty()) {
            return result(0, 0, 0, 0, 0);
        }

        List<List<Book>> groups = buildGroups(books);
        int rebuiltGroups = 0;
        int mergedBooks = 0;
        int aggregatedVersions = 0;

        for (List<Book> group : groups) {
            Book primary = group.get(0);
            bookVersionService.ensurePrimaryVersion(primary);
            if (group.size() == 1) {
                continue;
            }

            rebuiltGroups++;
            for (int index = 1; index < group.size(); index++) {
                Book duplicate = group.get(index);
                bookVersionService.ensurePrimaryVersion(duplicate);
                List<BookVersion> duplicateVersions =
                        bookVersionRepository
                                .findByBookOrderByPrimaryVersionDescCreatedAtAsc(duplicate);
                for (BookVersion version : duplicateVersions) {
                    version.setBook(primary);
                    version.setPrimaryVersion(false);
                }
                bookVersionRepository.saveAll(duplicateVersions);
                aggregatedVersions += duplicateVersions.size();

                mergeMetadata(primary, duplicate);
                mergeReadingProgress(primary, duplicate, user);
                moveBookmarks(primary, duplicate);
                moveHighlights(primary, duplicate, user);
                replaceInBookLists(primary, duplicate, user);
                hideAggregatedBook(duplicate);
                mergedBooks++;
            }
            bookRepository.save(primary);
        }

        return result(
                books.size(),
                rebuiltGroups,
                mergedBooks,
                aggregatedVersions,
                books.size() - mergedBooks);
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
        return switch (normalized) {
            case "未知", "未知作者", "unknown", "unknownauthor" -> "";
            default -> normalized;
        };
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

    private BookVersionRebuildResultDTO result(
            int scannedBooks,
            int rebuiltGroups,
            int mergedBooks,
            int aggregatedVersions,
            int remainingBooks) {
        return BookVersionRebuildResultDTO.builder()
                .scannedBooks(scannedBooks)
                .rebuiltGroups(rebuiltGroups)
                .mergedBooks(mergedBooks)
                .aggregatedVersions(aggregatedVersions)
                .remainingBooks(remainingBooks)
                .build();
    }

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
