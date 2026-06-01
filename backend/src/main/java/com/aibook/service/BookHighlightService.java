package com.aibook.service;

import com.aibook.dto.CreateHighlightRequest;
import com.aibook.dto.UpdateHighlightRequest;
import com.aibook.exception.ResourceNotFoundException;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookHighlight;
import com.aibook.model.entity.User;
import com.aibook.repository.BookHighlightRepository;
import com.aibook.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 书籍高亮/批注服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookHighlightService {

    private final BookHighlightRepository bookHighlightRepository;
    private final BookRepository bookRepository;

    /**
     * 获取书籍的所有高亮
     */
    public List<BookHighlight> getHighlights(User user, Long bookId) {
        Book book = findBook(user, bookId);
        return bookHighlightRepository.findByUserAndBookOrderByCreatedAtDesc(user, book);
    }

    /**
     * 创建高亮
     */
    @Transactional
    public BookHighlight createHighlight(User user, Long bookId, CreateHighlightRequest request) {
        Book book = findBook(user, bookId);

        // 检查是否已存在相同位置的高亮
        Optional<BookHighlight> existing = bookHighlightRepository.findByUserAndBookAndCfiRange(
                user, book, request.getCfiRange());
        if (existing.isPresent()) {
            throw new IllegalArgumentException("该位置已存在高亮");
        }

        BookHighlight highlight = BookHighlight.builder()
                .user(user)
                .book(book)
                .cfiRange(request.getCfiRange())
                .text(request.getText())
                .color(request.getColor() != null ? request.getColor() : "#ffff00")
                .chapter(request.getChapter())
                .note(request.getNote())
                .build();

        return bookHighlightRepository.save(highlight);
    }

    /**
     * 更新高亮
     */
    @Transactional
    public BookHighlight updateHighlight(User user, Long bookId, Long highlightId,
                                         UpdateHighlightRequest request) {
        Book book = findBook(user, bookId);
        BookHighlight highlight = bookHighlightRepository.findById(highlightId)
                .filter(h -> h.getUser().getId().equals(user.getId()))
                .filter(h -> h.getBook().getId().equals(bookId))
                .orElseThrow(() -> new ResourceNotFoundException("高亮", highlightId));

        if (request.getText() != null) {
            highlight.setText(request.getText());
        }
        if (request.getColor() != null) {
            highlight.setColor(request.getColor());
        }
        if (request.getChapter() != null) {
            highlight.setChapter(request.getChapter());
        }
        if (request.getNote() != null) {
            highlight.setNote(request.getNote());
        }

        return bookHighlightRepository.save(highlight);
    }

    /**
     * 删除高亮
     */
    @Transactional
    public void deleteHighlight(User user, Long bookId, Long highlightId) {
        Book book = findBook(user, bookId);
        BookHighlight highlight = bookHighlightRepository.findById(highlightId)
                .filter(h -> h.getUser().getId().equals(user.getId()))
                .filter(h -> h.getBook().getId().equals(bookId))
                .orElseThrow(() -> new ResourceNotFoundException("高亮", highlightId));

        bookHighlightRepository.delete(highlight);
    }

    // ==================== KOReader 高亮同步 ====================

    /**
     * 获取 KOReader 格式的高亮列表
     */
    public List<Map<String, Object>> getHighlightsForSync(User user, String documentId) {
        Book book = findBookByDocumentId(user, documentId);
        if (book == null) {
            return List.of();
        }

        List<BookHighlight> highlights = bookHighlightRepository.findByUserAndBookOrderByCreatedAtDesc(user, book);
        return highlights.stream()
                .map(this::convertToSyncFormat)
                .toList();
    }

    /**
     * 同步 KOReader 高亮（合并：新增不重复的）
     */
    @Transactional
    public Map<String, Object> syncHighlights(User user, String documentId,
                                              List<Map<String, Object>> clientHighlights) {
        Book book = findBookByDocumentId(user, documentId);
        if (book == null) {
            return Map.of("error", "Book not found");
        }

        int created = 0;
        int skipped = 0;

        for (Map<String, Object> highlightData : clientHighlights) {
            String cfiRange = (String) highlightData.get("pos");
            if (cfiRange == null) {
                cfiRange = (String) highlightData.get("cfiRange");
            }
            if (cfiRange == null) {
                skipped++;
                continue;
            }

            // 检查是否已存在
            Optional<BookHighlight> existing = bookHighlightRepository.findByUserAndBookAndCfiRange(
                    user, book, cfiRange);
            if (existing.isPresent()) {
                skipped++;
                continue;
            }

            // 创建新高亮
            BookHighlight highlight = BookHighlight.builder()
                    .user(user)
                    .book(book)
                    .cfiRange(cfiRange)
                    .text((String) highlightData.getOrDefault("text", ""))
                    .color((String) highlightData.getOrDefault("color", "#ffff00"))
                    .chapter((String) highlightData.get("chapter"))
                    .note((String) highlightData.get("note"))
                    .build();

            bookHighlightRepository.save(highlight);
            created++;
        }

        return Map.of(
            "created", created,
            "skipped", skipped,
            "status", "ok"
        );
    }

    // ==================== 私有方法 ====================

    private Book findBook(User user, Long bookId) {
        return bookRepository.findById(bookId)
                .filter(b -> b.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("书籍", bookId));
    }

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
        return bookByFilename.orElse(null);
    }

    private Map<String, Object> convertToSyncFormat(BookHighlight h) {
        return Map.of(
            "pos", h.getCfiRange(),
            "text", h.getText() != null ? h.getText() : "",
            "color", h.getColor() != null ? h.getColor() : "#ffff00",
            "chapter", h.getChapter() != null ? h.getChapter() : "",
            "note", h.getNote() != null ? h.getNote() : ""
        );
    }
}
