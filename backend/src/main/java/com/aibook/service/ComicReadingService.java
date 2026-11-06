package com.aibook.service;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookVersion;
import com.aibook.model.entity.ComicBookPreference;
import com.aibook.model.entity.User;
import com.aibook.model.entity.VersionReadingProgress;
import com.aibook.repository.BookRepository;
import com.aibook.repository.ComicBookPreferenceRepository;
import com.aibook.repository.VersionReadingProgressRepository;
import com.aibook.service.UserService.ComicPreferences;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ComicReadingService {

    private final BookRepository books;
    private final BookVersionService versions;
    private final VersionReadingProgressRepository positions;
    private final ReadingProgressService progressService;
    private final ComicArchiveService comics;
    private final BookParsingService parsing;
    private final ObjectMapper json;
    private final ComicBookPreferenceRepository bookPreferences;

    public record Manifest(String title, long versionId, String revision, int pageCount,
                           ComicPreferences bookPreferences) {}
    public record Position(int page, double offset, boolean finished, String revision, String token) {}
    public record ProgressUpdate(int page, double offset, boolean finished, String revision, String token) {}

    @Transactional
    public Manifest manifest(long bookId, long versionId, User user) {
        Book book = books.findOwnedBookForUpdate(bookId, user)
                .orElseThrow(ComicReadingService::missingBook);
        BookVersion version = versions.resolveVersion(book, versionId);
        var archive = comics.archive(source(version));
        if (Boolean.TRUE.equals(version.getPrimaryVersion())
                || book.getCoverUrl() == null || book.getCoverUrl().isBlank()) {
            parsing.extractComicCover(book, archive.source().path());
        }
        ComicPreferences preferences = bookPreferences.findByBookAndUser(book, user)
                .map(stored -> new ComicPreferences(stored.getMode(), stored.getDirection(), stored.getFit()))
                .orElse(null);
        return new Manifest(book.getTitle(), version.getId(),
                archive.source().revision(), archive.pageCount(), preferences);
    }

    @Transactional
    public void saveBookPreferences(long bookId, long versionId, User user,
            ComicPreferences preferences) {
        Book book = lockComicBook(bookId, versionId, user);
        UserService.validateComicPreferences(preferences);
        var stored = bookPreferences.findByBookAndUser(book, user)
                .orElseGet(() -> new ComicBookPreference(book, user));
        stored.setMode(preferences.mode());
        stored.setDirection(preferences.direction());
        stored.setFit(preferences.fit());
        bookPreferences.save(stored);
    }

    @Transactional
    public void resetBookPreferences(long bookId, long versionId, User user) {
        Book book = lockComicBook(bookId, versionId, user);
        bookPreferences.findByBookAndUser(book, user).ifPresent(bookPreferences::delete);
    }

    private Book lockComicBook(long bookId, long versionId, User user) {
        // Serialize preference writes even before a per-book row exists.
        Book book = books.findOwnedBookForUpdate(bookId, user)
                .orElseThrow(ComicReadingService::missingBook);
        requireComic(versions.resolveVersion(book, versionId));
        return book;
    }

    public ComicArchiveService.Page page(long bookId, long versionId, User user,
            String revision, int page) {
        BookVersion version = versions.resolveVersion(ownedBook(bookId, user), versionId);
        var source = source(version);
        requireRevision(source, revision);
        var image = comics.page(comics.archive(source), page);
        requireRevision(source(version), revision);
        return image;
    }

    @Transactional
    public Position position(long bookId, long versionId, User user) {
        BookVersion version = versions.resolveVersion(ownedBook(bookId, user), versionId);
        requireComic(version);
        return readPosition(positions.findByUserAndVersion(user, version).orElse(null));
    }

    @Transactional
    public Position save(long bookId, long versionId, User user, ProgressUpdate update) {
        // The parent row also serializes the first write, when there is no position row to lock.
        Book book = books.findOwnedBookForUpdate(bookId, user)
                .orElseThrow(ComicReadingService::missingBook);
        BookVersion version = versions.resolveVersion(book, versionId);
        var source = source(version);
        requireRevision(source, update.revision());
        int count = comics.archive(source).pageCount();
        if (update.page() < 1 || update.page() > count || !Double.isFinite(update.offset())
                || update.offset() < 0 || update.offset() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "漫画阅读位置无效");
        }

        VersionReadingProgress progress = positions.findByUserAndVersionForUpdate(user, version)
                .orElseGet(() -> VersionReadingProgress.builder().version(version).user(user).build());
        Position current = readPosition(progress);
        if (!Objects.equals(current.token(), update.token())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "阅读进度已在另一设备更新");
        }
        boolean sameFile = Objects.equals(current.revision(), update.revision());
        boolean finished = (sameFile && current.finished())
                || (update.finished() && update.page() == count);
        requireRevision(source(version), update.revision());
        Position next = new Position(update.page(), update.offset(), finished, update.revision(),
                UUID.randomUUID().toString());
        int percent = finished ? 100 : Math.min(99, (int) (100L * update.page() / count));
        progressService.savePosition(book, user, progress, Integer.toString(update.page()),
                "第 " + update.page() + " / " + count + " 页",
                (int) Math.round(update.offset() * 100), percent, serialize(next));
        return next;
    }

    private Position readPosition(VersionReadingProgress progress) {
        if (progress != null && progress.getLocator() != null) {
            try {
                Position position = json.readValue(progress.getLocator(), Position.class);
                if (position != null && position.page() >= 1 && position.token() != null) {
                    return position;
                }
            } catch (JsonProcessingException ignored) {
                // Other readers may have left a different locator format on an old version.
            }
        }
        return new Position(1, 0, false, null, null);
    }

    private String serialize(Position position) {
        try {
            return json.writeValueAsString(position);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("漫画阅读位置无法保存", exception);
        }
    }

    private ComicArchiveService.Source source(BookVersion version) {
        requireComic(version);
        return comics.source(version.getFilePath(), version.getFileHash());
    }

    private static void requireComic(BookVersion version) {
        if (!"cbz".equalsIgnoreCase(version.getFormat())) {
            throw new ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE, "该版本暂不支持漫画在线阅读");
        }
    }

    private static void requireRevision(ComicArchiveService.Source source, String revision) {
        if (!source.revision().equals(revision)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "漫画文件已更新，请重新加载");
        }
    }

    private Book ownedBook(long id, User user) {
        return books.findByIdAndUserAndDeletedAtIsNull(id, user)
                .orElseThrow(ComicReadingService::missingBook);
    }

    private static ResponseStatusException missingBook() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "书籍不存在");
    }
}
