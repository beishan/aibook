package com.aibook.service.crawler;

import com.aibook.model.entity.CrawlerBook;
import com.aibook.model.entity.User;
import com.aibook.repository.CrawlerBookRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 单本和批量手动入库的统一入口，在入库事务结束后记录失败。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CrawlerLibraryImportService {
    private final CrawlerExportService exportService;
    private final CrawlerBookRepository bookRepository;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Long importLibrary(User user, Long bookId, List<String> formats) {
        try {
            // 经由另一个 Spring Bean 调用，异常及提交失败均在事务回滚后被捕获。
            return exportService.importLibrary(user, bookId, formats);
        } catch (RuntimeException exception) {
            try {
                bookRepository.updateImportFailure(bookId, user, CrawlerBook.ImportStatus.FAILED);
            } catch (RuntimeException statusException) {
                exception.addSuppressed(statusException);
                log.error("保存入库失败状态失败: book={}", bookId, statusException);
            }
            throw exception;
        }
    }
}
