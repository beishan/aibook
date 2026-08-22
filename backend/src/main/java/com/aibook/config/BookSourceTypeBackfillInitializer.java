package com.aibook.config;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.SystemConfig;
import com.aibook.repository.BookRepository;
import com.aibook.repository.BookSourceTypeBackfillProjection;
import com.aibook.repository.SystemConfigRepository;
import jakarta.persistence.EntityManager;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 回填历史书籍的首次入库方式。
 *
 * <p>迁移仅根据升级前的上传目录判断：上传目录内的文件标记为上传，其余标记为目录扫描。
 * 分页游标和完成标记使重复启动不会重复处理已完成的数据。</p>
 */
@Component
@Slf4j
public class BookSourceTypeBackfillInitializer {

    static final String MIGRATION_KEY = "migration.book-source-type.v1";
    private static final int BATCH_SIZE = 500;

    private final BookRepository bookRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final EntityManager entityManager;
    private final String uploadPath;

    public BookSourceTypeBackfillInitializer(
            BookRepository bookRepository,
            SystemConfigRepository systemConfigRepository,
            EntityManager entityManager,
            @Value("${upload.path:./uploads}") String uploadPath) {
        this.bookRepository = bookRepository;
        this.systemConfigRepository = systemConfigRepository;
        this.entityManager = entityManager;
        this.uploadPath = uploadPath;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfill() {
        if (systemConfigRepository.findById(MIGRATION_KEY)
                .map(SystemConfig::getConfigValue)
                .filter("complete"::equals)
                .isPresent()) {
            return;
        }

        Path normalizedUploadPath = normalizedPath(uploadPath);
        long afterId = 0L;
        int updated = 0;
        while (true) {
            List<BookSourceTypeBackfillProjection> books =
                    bookRepository.findSourceTypeBackfillCandidatesAfterId(
                            afterId, PageRequest.of(0, BATCH_SIZE));
            if (books.isEmpty()) {
                break;
            }
            for (BookSourceTypeBackfillProjection book : books) {
                Book.SourceType sourceType = isInUploadDirectory(
                        normalizedPath(book.getFilePath()), normalizedUploadPath)
                        ? Book.SourceType.UPLOAD
                        : Book.SourceType.DIRECTORY_SCAN;
                entityManager.getReference(Book.class, book.getId()).setSourceType(sourceType);
                updated++;
            }
            afterId = books.get(books.size() - 1).getId();
            entityManager.flush();
            entityManager.clear();
        }

        systemConfigRepository.save(SystemConfig.builder()
                .configKey(MIGRATION_KEY)
                .configValue("complete")
                .description("历史书籍首次入库方式回填已完成")
                .build());
        log.info("历史书籍首次入库方式回填完成，更新 {} 本", updated);
    }

    private boolean isInUploadDirectory(Path filePath, Path uploadDirectory) {
        return filePath != null && uploadDirectory != null && filePath.startsWith(uploadDirectory);
    }

    private Path normalizedPath(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Path.of(value).toAbsolutePath().normalize();
        } catch (InvalidPathException exception) {
            log.warn("无法规范化历史书籍来源路径: {}", value);
            return null;
        }
    }
}
