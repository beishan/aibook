package com.aibook.service;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.ReadingProgress;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.ReadingProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 阅读进度服务
 */
@Service
@RequiredArgsConstructor
public class ReadingProgressService {

    private final ReadingProgressRepository readingProgressRepository;
    private final BookRepository bookRepository;

    /**
     * 获取阅读进度
     */
    public ReadingProgress getProgress(Long bookId, User user) {
        Book book = bookRepository.findByIdAndUserAndDeletedAtIsNull(bookId, user)
                .orElseThrow(() -> new RuntimeException("书籍不存在"));

        return readingProgressRepository.findByUserAndBook(user, book)
                .orElse(ReadingProgress.builder()
                        .book(book)
                        .user(user)
                        .currentChapter("")
                        .currentChapterTitle("")
                        .chapterProgress(0)
                        .totalProgress(0)
                        .readingTimeSeconds(0L)
                        .build());
    }

    /**
     * 保存阅读进度
     */
    @Transactional
    public ReadingProgress saveProgress(Long bookId, User user, String currentChapter,
                                       String currentChapterTitle, Integer chapterProgress,
                                       Integer totalProgress) {
        Book book = bookRepository.findByIdAndUserAndDeletedAtIsNull(bookId, user)
                .orElseThrow(() -> new RuntimeException("书籍不存在"));

        ReadingProgress progress = readingProgressRepository.findByUserAndBook(user, book)
                .orElse(ReadingProgress.builder()
                        .book(book)
                        .user(user)
                        .build());

        progress.setCurrentChapter(currentChapter);
        if (currentChapterTitle != null && !currentChapterTitle.isBlank()) {
            progress.setCurrentChapterTitle(currentChapterTitle);
        } else if (currentChapter != null
                && !currentChapter.isBlank()
                && !currentChapter.startsWith("epubcfi(")) {
            // 兼容尚未传递独立章节标题的文本阅读客户端。
            progress.setCurrentChapterTitle(currentChapter);
        }
        progress.setChapterProgress(chapterProgress);
        progress.setTotalProgress(totalProgress);
        progress.setLastReadAt(LocalDateTime.now());

        // 如果进度为100%，自动标记为已读完
        if (totalProgress != null && totalProgress >= 100) {
            book.setReadingStatus(Book.ReadingStatus.FINISHED);
            bookRepository.save(book);
        } else if (totalProgress != null && totalProgress > 0) {
            book.setReadingStatus(Book.ReadingStatus.READING);
            bookRepository.save(book);
        }

        return readingProgressRepository.save(progress);
    }

    /**
     * 更新阅读时长
     */
    @Transactional
    public ReadingProgress updateReadingTime(Long bookId, User user, long additionalSeconds) {
        Book book = bookRepository.findByIdAndUserAndDeletedAtIsNull(bookId, user)
                .orElseThrow(() -> new RuntimeException("书籍不存在"));

        ReadingProgress progress = readingProgressRepository.findByUserAndBook(user, book)
                .orElse(ReadingProgress.builder()
                        .book(book)
                        .user(user)
                        .build());

        progress.setReadingTimeSeconds(progress.getReadingTimeSeconds() + additionalSeconds);
        progress.setLastReadAt(LocalDateTime.now());

        return readingProgressRepository.save(progress);
    }

    /**
     * 获取最近阅读的书籍
     */
    public Optional<ReadingProgress> getRecentlyRead(User user) {
        return readingProgressRepository
                .findTopByUserAndBookDeletedAtIsNullOrderByLastReadAtDesc(user);
    }
}
