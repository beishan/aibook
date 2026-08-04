package com.aibook.config;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookScanSource;
import com.aibook.model.entity.ScanDirectory;
import com.aibook.repository.BookRepository;
import com.aibook.repository.BookScanSourceRepository;
import com.aibook.repository.ScanDirectoryRepository;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 将旧书籍按路径回填为扫描目录来源。使用 Path.normalize().startsWith()，
 * 避免 /books/a 错误匹配 /books/archive 这样的字符串前缀问题。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookScanSourceBackfillInitializer {

    private final ScanDirectoryRepository scanDirectoryRepository;
    private final BookRepository bookRepository;
    private final BookScanSourceRepository bookScanSourceRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfill() {
        List<Book> books = bookRepository.findAll();
        int created = 0;
        for (ScanDirectory directory : scanDirectoryRepository.findAll()) {
            Path directoryPath = normalizedPath(directory.getPath());
            if (directoryPath == null || directory.getUser() == null) {
                continue;
            }
            for (Book book : books) {
                if (book.getUser() == null
                        || !Objects.equals(book.getUser().getId(), directory.getUser().getId())) {
                    continue;
                }
                Path bookPath = normalizedPath(book.getFilePath());
                if (bookPath != null && bookPath.startsWith(directoryPath)
                        && !bookScanSourceRepository.existsByBookAndScanDirectory(book, directory)) {
                    bookScanSourceRepository.save(BookScanSource.builder()
                            .book(book)
                            .scanDirectory(directory)
                            .user(directory.getUser())
                            .build());
                    created++;
                }
            }
        }
        if (created > 0) {
            log.info("已回填 {} 条书籍扫描目录来源", created);
        }
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
}
