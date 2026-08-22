package com.aibook.config;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookScanSource;
import com.aibook.model.entity.ScanDirectory;
import com.aibook.model.entity.SystemConfig;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.BookScanSourceBackfillProjection;
import com.aibook.repository.BookScanSourceKeyProjection;
import com.aibook.repository.BookScanSourceRepository;
import com.aibook.repository.BookVersionRepository;
import com.aibook.repository.BookVersionScanSourceBackfillProjection;
import com.aibook.repository.ScanDirectoryRepository;
import com.aibook.repository.SystemConfigRepository;
import jakarta.persistence.EntityManager;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 将升级前已有书籍按规范化路径回填为扫描目录来源。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookScanSourceBackfillInitializer {

    /** v2 also checks every BookVersion.filePath; v1 completion must not skip this repair. */
    static final String MIGRATION_KEY = "migration.book-scan-sources.v2";
    private static final int BATCH_SIZE = 500;

    private final ScanDirectoryRepository scanDirectoryRepository;
    private final BookRepository bookRepository;
    private final BookVersionRepository bookVersionRepository;
    private final BookScanSourceRepository bookScanSourceRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final EntityManager entityManager;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfill() {
        if (systemConfigRepository.findById(MIGRATION_KEY)
                .map(SystemConfig::getConfigValue)
                .filter("complete"::equals)
                .isPresent()) {
            return;
        }

        List<DirectorySource> directories = scanDirectoryRepository.findAll().stream()
                .map(this::toDirectorySource)
                .filter(java.util.Objects::nonNull)
                .toList();

        int created = backfillBookPaths(directories);
        created += backfillVersionPaths(directories);

        systemConfigRepository.save(SystemConfig.builder()
                .configKey(MIGRATION_KEY)
                .configValue("complete")
                .description("历史书籍及版本扫描目录来源回填已完成")
                .build());
        log.info("历史书籍及版本扫描目录来源回填完成，新增 {} 条关联", created);
    }

    private int backfillBookPaths(List<DirectorySource> directories) {
        long afterId = 0L;
        int created = 0;
        while (true) {
            List<BookScanSourceBackfillProjection> books =
                    bookRepository.findBackfillCandidatesAfterId(
                            afterId, PageRequest.of(0, BATCH_SIZE));
            if (books.isEmpty()) {
                break;
            }
            created += backfillPaths(
                    books.stream()
                            .map(book -> new PathCandidate(
                                    book.getId(), book.getUserId(), book.getFilePath()))
                            .toList(),
                    directories);
            afterId = books.get(books.size() - 1).getId();
            entityManager.flush();
            entityManager.clear();
        }
        return created;
    }

    private int backfillVersionPaths(List<DirectorySource> directories) {
        long afterId = 0L;
        int created = 0;
        while (true) {
            List<BookVersionScanSourceBackfillProjection> versions =
                    bookVersionRepository.findScanSourceBackfillCandidatesAfterId(
                            afterId, PageRequest.of(0, BATCH_SIZE));
            if (versions.isEmpty()) {
                break;
            }
            created += backfillPaths(
                    versions.stream()
                            .map(version -> new PathCandidate(
                                    version.getBookId(), version.getUserId(), version.getFilePath()))
                            .toList(),
                    directories);
            afterId = versions.get(versions.size() - 1).getId();
            entityManager.flush();
            entityManager.clear();
        }
        return created;
    }

    private int backfillPaths(
            List<PathCandidate> candidates,
            List<DirectorySource> directories) {
        List<Long> bookIds = candidates.stream().map(PathCandidate::bookId).distinct().toList();
        Set<SourceKey> existing = new HashSet<>();
        for (BookScanSourceKeyProjection key : bookScanSourceRepository.findKeysByBookIds(bookIds)) {
            existing.add(new SourceKey(key.getBookId(), key.getScanDirectoryId()));
        }

        List<BookScanSource> sources = new ArrayList<>();
        for (PathCandidate candidate : candidates) {
            Path bookPath = normalizedPath(candidate.filePath());
            if (bookPath == null) {
                continue;
            }
            for (DirectorySource directory : directories) {
                SourceKey key = new SourceKey(candidate.bookId(), directory.id());
                if (directory.userId().equals(candidate.userId())
                        && bookPath.startsWith(directory.path())
                        && existing.add(key)) {
                    sources.add(BookScanSource.builder()
                            .book(entityManager.getReference(Book.class, candidate.bookId()))
                            .scanDirectory(entityManager.getReference(
                                    ScanDirectory.class, directory.id()))
                            .user(entityManager.getReference(User.class, candidate.userId()))
                            .build());
                }
            }
        }
        if (!sources.isEmpty()) {
            bookScanSourceRepository.saveAll(sources);
        }
        return sources.size();
    }

    private DirectorySource toDirectorySource(ScanDirectory directory) {
        Path path = normalizedPath(directory.getPath());
        if (path == null || directory.getUser() == null || directory.getUser().getId() == null) {
            return null;
        }
        return new DirectorySource(directory.getId(), directory.getUser().getId(), path);
    }

    private Path normalizedPath(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Path.of(value).normalize();
        } catch (InvalidPathException exception) {
            log.warn("跳过无效路径: {}", value);
            return null;
        }
    }

    private record DirectorySource(Long id, Long userId, Path path) {}

    private record PathCandidate(Long bookId, Long userId, String filePath) {}

    private record SourceKey(Long bookId, Long directoryId) {}
}
