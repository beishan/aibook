package com.aibook.service;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookScanSource;
import com.aibook.model.entity.Category;
import com.aibook.model.entity.OperationLog;
import com.aibook.model.entity.ScanDirectory;
import com.aibook.model.entity.User;
import com.aibook.repository.BookScanSourceRepository;
import com.aibook.repository.BookRepository;
import com.aibook.repository.CategoryRepository;
import com.aibook.repository.ScanDirectoryRepository;
import com.aibook.repository.UserRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在扫描工作线程自己的事务中保存书籍。
 */
@Service
@RequiredArgsConstructor
public class ScannedBookPersistenceService {

    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final OperationLogService operationLogService;
    private final BookScanSourceRepository bookScanSourceRepository;
    private final ScanDirectoryRepository scanDirectoryRepository;
    private final RandomBookCoverService randomBookCoverService;
    private final AuthorService authorService;

    /**
     * 使用当前事务创建的实体引用，避免跨线程共享 Hibernate 代理。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Book save(Book book, Long userId, Long categoryId) {
        return save(book, userId, categoryId, null);
    }

    /** 保存新书，并在来自受管扫描目录时登记来源。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Book save(Book book, Long userId, Long categoryId, Long directoryId) {
        User user = userRepository.getReferenceById(userId);
        Category category = categoryId == null
                ? null
                : categoryRepository.getReferenceById(categoryId);

        book.setUser(user);
        book.setCategory(category);
        book.setSourceType(Book.SourceType.DIRECTORY_SCAN);
        Book savedBook = bookRepository.save(book);
        authorService.synchronizeBook(savedBook);
        savedBook = randomBookCoverService.assignIfMissing(savedBook, user);
        recordSource(savedBook, user, directoryId);
        operationLogService.record(
                user, OperationLog.Action.IMPORT_BOOK, savedBook,
                "导入书籍《" + savedBook.getTitle() + "》", "来源：目录扫描");
        return savedBook;
    }

    /** 已存在书籍再次被扫描到时，幂等补记来源。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordExistingSource(String fileHash, Long userId, Long directoryId) {
        if (directoryId == null || fileHash == null) {
            return;
        }
        bookRepository.findByFileHash(fileHash)
                .filter(book -> book.getUser() != null
                        && Objects.equals(book.getUser().getId(), userId))
                .ifPresent(book -> recordSource(
                        book, userRepository.getReferenceById(userId), directoryId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordExistingSource(Book book, Long userId, Long directoryId) {
        if (book == null || directoryId == null || book.getUser() == null
                || !Objects.equals(book.getUser().getId(), userId)) {
            return;
        }
        recordSource(book, userRepository.getReferenceById(userId), directoryId);
    }

    private void recordSource(Book book, User user, Long directoryId) {
        if (directoryId == null) {
            return;
        }
        ScanDirectory directory = scanDirectoryRepository.findById(directoryId).orElse(null);
        if (directory == null || directory.getUser() == null
                || !Objects.equals(directory.getUser().getId(), user.getId())
                || bookScanSourceRepository.existsByBookAndScanDirectory(book, directory)) {
            return;
        }
        bookScanSourceRepository.save(BookScanSource.builder()
                .book(book)
                .scanDirectory(directory)
                .user(user)
                .build());
    }
}
