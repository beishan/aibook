package com.aibook.service;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookVersion;
import com.aibook.model.entity.ReadingProgress;
import com.aibook.model.entity.User;
import com.aibook.model.entity.VersionReadingProgress;
import com.aibook.repository.BookRepository;
import com.aibook.repository.ReadingProgressRepository;
import com.aibook.repository.VersionReadingProgressRepository;
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
    private final VersionReadingProgressRepository versionProgressRepository;
    private final BookRepository bookRepository;
    private final BookVersionService bookVersionService;

    /**
     * 获取阅读进度
     */
    @Transactional
    public com.aibook.dto.ReadingProgressDTO getProgress(
            Long bookId, Long versionId, User user) {
        Book book = bookRepository.findByIdAndUserAndDeletedAtIsNull(bookId, user)
                .orElseThrow(() -> new RuntimeException("书籍不存在"));
        BookVersion version = bookVersionService.resolveVersion(book, versionId);

        return versionProgressRepository.findByUserAndVersion(user, version)
                .map(this::toDTO)
                .orElseGet(() -> emptyProgress(book, version));
    }

    /**
     * 保存阅读进度
     */
    @Transactional
    public com.aibook.dto.ReadingProgressDTO saveProgress(
            Long bookId,
            Long versionId,
            User user,
            String currentChapter,
            String currentChapterTitle,
            Integer chapterProgress,
            Integer totalProgress) {
        return saveProgress(bookId, versionId, user, currentChapter,
                currentChapterTitle, chapterProgress, totalProgress, null);
    }

    @Transactional
    public com.aibook.dto.ReadingProgressDTO saveProgress(
            Long bookId,
            Long versionId,
            User user,
            String currentChapter,
            String currentChapterTitle,
            Integer chapterProgress,
            Integer totalProgress,
            String locator) {
        Book book = bookRepository.findByIdAndUserAndDeletedAtIsNull(bookId, user)
                .orElseThrow(() -> new RuntimeException("书籍不存在"));
        BookVersion version = bookVersionService.resolveVersion(book, versionId);

        VersionReadingProgress progress =
                versionProgressRepository.findByUserAndVersion(user, version)
                .orElse(VersionReadingProgress.builder()
                        .version(version)
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
        progress.setLocator(locator);
        progress.setChapterProgress(percent(chapterProgress));
        progress.setTotalProgress(percent(totalProgress));
        progress.setLastReadAt(LocalDateTime.now());

        // 保存进度即表示用户已经开始阅读；即使首屏进度仍为 0，也应进入“正在阅读”。
        if (percent(totalProgress) >= 100) {
            book.setReadingStatus(Book.ReadingStatus.FINISHED);
        } else {
            book.setReadingStatus(Book.ReadingStatus.READING);
        }
        bookRepository.save(book);

        VersionReadingProgress saved = versionProgressRepository.save(progress);
        syncAggregateProgress(book, user, saved);
        return toDTO(saved);
    }

    /**
     * 更新阅读时长
     */
    @Transactional
    public com.aibook.dto.ReadingProgressDTO updateReadingTime(
            Long bookId, Long versionId, User user, long additionalSeconds) {
        return updateReadingTime(bookId, versionId, user, additionalSeconds, null);
    }

    @Transactional
    public com.aibook.dto.ReadingProgressDTO updateReadingTime(
            Long bookId, Long versionId, User user, long seconds, String sessionId) {
        Book book = bookRepository.findByIdAndUserAndDeletedAtIsNull(bookId, user)
                .orElseThrow(() -> new RuntimeException("书籍不存在"));
        BookVersion version = bookVersionService.resolveVersion(book, versionId);

        VersionReadingProgress progress =
                versionProgressRepository.findByUserAndVersion(user, version)
                .orElse(VersionReadingProgress.builder()
                        .version(version)
                        .user(user)
                        .build());

        long currentSeconds = progress.getReadingTimeSeconds() == null ? 0L : progress.getReadingTimeSeconds();
        long safeSeconds = Math.max(0L, Math.min(seconds, 86_400L));
        if (sessionId != null && !sessionId.isBlank()) {
            String normalizedSessionId = sessionId.length() > 100
                    ? sessionId.substring(0, 100) : sessionId;
            long previousElapsed = normalizedSessionId.equals(progress.getReadingSessionId())
                    && progress.getReadingSessionElapsedSeconds() != null
                    ? progress.getReadingSessionElapsedSeconds() : 0L;
            progress.setReadingTimeSeconds(currentSeconds + Math.max(0L, safeSeconds - previousElapsed));
            progress.setReadingSessionId(normalizedSessionId);
            progress.setReadingSessionElapsedSeconds(Math.max(previousElapsed, safeSeconds));
        } else {
            // 保持 KOReader 等旧客户端按增量秒数上报的兼容行为。
            progress.setReadingTimeSeconds(currentSeconds + safeSeconds);
        }
        progress.setLastReadAt(LocalDateTime.now());

        // 某些阅读器会先上报阅读时长再上报页面进度，同样应出现在“正在阅读”中。
        if (book.getReadingStatus() != Book.ReadingStatus.FINISHED) {
            book.setReadingStatus(Book.ReadingStatus.READING);
            bookRepository.save(book);
        }

        VersionReadingProgress saved = versionProgressRepository.save(progress);
        syncAggregateProgress(book, user, saved);
        return toDTO(saved);
    }

    /**
     * 获取最近阅读的书籍
     */
    public Optional<ReadingProgress> getRecentlyRead(User user) {
        return readingProgressRepository
                .findTopByUserAndBookDeletedAtIsNullOrderByLastReadAtDesc(user);
    }

    private void syncAggregateProgress(
            Book book, User user, VersionReadingProgress versionProgress) {
        ReadingProgress aggregate = readingProgressRepository.findByUserAndBook(user, book)
                .orElse(ReadingProgress.builder()
                        .book(book)
                        .user(user)
                        .build());
        aggregate.setCurrentChapter(versionProgress.getCurrentChapter());
        aggregate.setCurrentChapterTitle(versionProgress.getCurrentChapterTitle());
        aggregate.setLocator(versionProgress.getLocator());
        aggregate.setChapterProgress(versionProgress.getChapterProgress());
        aggregate.setTotalProgress(versionProgress.getTotalProgress());
        aggregate.setReadingTimeSeconds(versionProgress.getReadingTimeSeconds());
        aggregate.setLastReadAt(versionProgress.getLastReadAt());
        readingProgressRepository.save(aggregate);
    }

    private com.aibook.dto.ReadingProgressDTO emptyProgress(
            Book book, BookVersion version) {
        return com.aibook.dto.ReadingProgressDTO.builder()
                .bookId(book.getId())
                .versionId(version.getId())
                .currentChapter("")
                .currentChapterTitle("")
                .locator(null)
                .chapterProgress(0)
                .totalProgress(0)
                .readingTimeSeconds(0L)
                .build();
    }

    private com.aibook.dto.ReadingProgressDTO toDTO(
            VersionReadingProgress progress) {
        return com.aibook.dto.ReadingProgressDTO.builder()
                .id(progress.getId())
                .bookId(progress.getVersion().getBook().getId())
                .versionId(progress.getVersion().getId())
                .currentChapter(progress.getCurrentChapter())
                .currentChapterTitle(progress.getCurrentChapterTitle())
                .locator(progress.getLocator())
                .chapterProgress(progress.getChapterProgress())
                .totalProgress(progress.getTotalProgress())
                .readingTimeSeconds(progress.getReadingTimeSeconds())
                .lastReadAt(progress.getLastReadAt())
                .createdAt(progress.getCreatedAt())
                .updatedAt(progress.getUpdatedAt())
                .build();
    }

    private int percent(Integer value) {
        return value == null ? 0 : Math.max(0, Math.min(100, value));
    }
}
