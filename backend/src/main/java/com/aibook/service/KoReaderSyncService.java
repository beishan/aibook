package com.aibook.service;

import com.aibook.exception.ResourceNotFoundException;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.ReadingProgress;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.ReadingProgressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * KOReader 进度同步服务
 * 支持 KOReader 阅读器的进度同步功能
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KoReaderSyncService {

    private final ReadingProgressRepository readingProgressRepository;
    private final BookRepository bookRepository;

    /**
     * 获取书籍的阅读进度（KOReader 格式）
     */
    public Map<String, Object> getProgress(User user, String documentId) {
        Book book = findBookByDocumentId(user, documentId);
        if (book == null) {
            throw new ResourceNotFoundException("Book not found: " + documentId);
        }

        Optional<ReadingProgress> progressOpt = readingProgressRepository.findByUserAndBook(user, book);

        if (progressOpt.isEmpty()) {
            return Map.of(
                "document", documentId,
                "progress", 0,
                "percentage", 0
            );
        }

        ReadingProgress progress = progressOpt.get();
        Map<String, Object> result = new HashMap<>();
        result.put("document", documentId);
        result.put("progress", progress.getTotalProgress());
        result.put("percentage", progress.getTotalProgress());
        result.put("chapter", progress.getCurrentChapter());
        result.put("chapter_progress", progress.getChapterProgress());
        result.put("last_read", progress.getLastReadAt() != null ?
            progress.getLastReadAt().toString() : null);

        return result;
    }

    /**
     * 保存阅读进度（KOReader 格式）
     */
    @Transactional
    public Map<String, Object> saveProgress(User user, String documentId, Map<String, Object> progressData) {
        Book book = findBookByDocumentId(user, documentId);
        if (book == null) {
            throw new ResourceNotFoundException("Book not found: " + documentId);
        }

        ReadingProgress progress = readingProgressRepository.findByUserAndBook(user, book)
                .orElse(ReadingProgress.builder()
                        .book(book)
                        .user(user)
                        .build());

        // 解析 KOReader 进度数据
        if (progressData.containsKey("progress")) {
            Object progressVal = progressData.get("progress");
            if (progressVal instanceof Number) {
                progress.setTotalProgress(((Number) progressVal).intValue());
            }
        }

        if (progressData.containsKey("percentage")) {
            Object percentageVal = progressData.get("percentage");
            if (percentageVal instanceof Number) {
                progress.setTotalProgress(((Number) percentageVal).intValue());
            }
        }

        if (progressData.containsKey("chapter")) {
            progress.setCurrentChapter((String) progressData.get("chapter"));
        }

        if (progressData.containsKey("chapter_progress")) {
            Object chapterProgressVal = progressData.get("chapter_progress");
            if (chapterProgressVal instanceof Number) {
                progress.setChapterProgress(((Number) chapterProgressVal).intValue());
            }
        }

        progress.setLastReadAt(LocalDateTime.now());

        // 更新书籍状态
        if (progress.getTotalProgress() != null && progress.getTotalProgress() >= 100) {
            book.setReadingStatus(Book.ReadingStatus.FINISHED);
        } else if (progress.getTotalProgress() != null && progress.getTotalProgress() > 0) {
            book.setReadingStatus(Book.ReadingStatus.READING);
        }
        bookRepository.save(book);

        ReadingProgress saved = readingProgressRepository.save(progress);

        Map<String, Object> result = new HashMap<>();
        result.put("document", documentId);
        result.put("progress", saved.getTotalProgress());
        result.put("percentage", saved.getTotalProgress());
        result.put("status", "ok");

        return result;
    }

    /**
     * 批量同步进度
     */
    @Transactional
    public Map<String, Object> syncProgress(User user, Map<String, Object> syncData) {
        Map<String, Object> results = new HashMap<>();

        if (syncData.containsKey("progress")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> progressMap = (Map<String, Object>) syncData.get("progress");
            for (Map.Entry<String, Object> entry : progressMap.entrySet()) {
                String documentId = entry.getKey();
                @SuppressWarnings("unchecked")
                Map<String, Object> progressData = (Map<String, Object>) entry.getValue();
                try {
                    Map<String, Object> result = saveProgress(user, documentId, progressData);
                    results.put(documentId, result);
                } catch (ResourceNotFoundException e) {
                    results.put(documentId, Map.of("error", e.getMessage()));
                }
            }
        }

        return Map.of(
            "results", results,
            "status", "ok"
        );
    }

    /**
     * 根据 document ID 查找书籍
     * KOReader 使用文件名或 md5 作为 document ID
     */
    private Book findBookByDocumentId(User user, String documentId) {
        // 尝试通过文件哈希查找
        Optional<Book> bookByHash = bookRepository.findByFileHash(documentId);
        if (bookByHash.isPresent() && bookByHash.get().getUser().getId().equals(user.getId())) {
            return bookByHash.get();
        }

        // 尝试通过 ID 查找
        try {
            Long id = Long.parseLong(documentId);
            Optional<Book> bookById = bookRepository.findById(id);
            if (bookById.isPresent() && bookById.get().getUser().getId().equals(user.getId())) {
                return bookById.get();
            }
        } catch (NumberFormatException ignored) {
        }

        // 尝试通过文件名查找
        Optional<Book> bookByFilename = bookRepository.findByUserAndFilename(user, documentId);
        if (bookByFilename.isPresent()) {
            return bookByFilename.get();
        }

        // 最后回退：遍历所有书籍
        var books = bookRepository.findByUser(user);
        for (Book book : books) {
            String filename = book.getTitle() + "." + book.getFormat();
            if (filename.equals(documentId) || book.getFilePath().endsWith(documentId)) {
                return book;
            }
        }

        return null;
    }
}
