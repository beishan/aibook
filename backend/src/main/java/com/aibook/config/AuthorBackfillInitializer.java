package com.aibook.config;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.SystemConfig;
import com.aibook.repository.BookRepository;
import com.aibook.repository.SystemConfigRepository;
import com.aibook.service.AuthorService;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 一次性补齐升级前书籍的规范化作者关联，避免列表请求承担迁移工作。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthorBackfillInitializer {

    static final String MIGRATION_KEY = "migration.book-authors.v1";
    private static final int BATCH_SIZE = 200;

    private final BookRepository bookRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final AuthorService authorService;
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

        long afterId = 0L;
        int updated = 0;
        while (true) {
            List<Book> books = bookRepository.findAuthorSynchronizationCandidatesAfterId(
                    afterId, PageRequest.of(0, BATCH_SIZE));
            if (books.isEmpty()) break;
            for (Book book : books) {
                authorService.synchronizeBook(book);
                updated++;
            }
            afterId = books.get(books.size() - 1).getId();
            entityManager.flush();
            entityManager.clear();
        }

        systemConfigRepository.save(SystemConfig.builder()
                .configKey(MIGRATION_KEY)
                .configValue("complete")
                .description("历史书籍作者关联回填已完成")
                .build());
        log.info("历史书籍作者关联回填完成，处理 {} 本", updated);
    }
}
