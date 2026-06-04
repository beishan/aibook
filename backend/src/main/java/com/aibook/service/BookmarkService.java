package com.aibook.service;

import com.aibook.dto.CreateBookmarkRequest;
import com.aibook.exception.ResourceNotFoundException;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.Bookmark;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.BookmarkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 书签服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final BookRepository bookRepository;

    /**
     * 获取书籍的所有书签
     */
    public List<Bookmark> getBookmarks(User user, Long bookId) {
        Book book = findBook(user, bookId);
        return bookmarkRepository.findByUserAndBookOrderByCreatedAtDesc(user, book);
    }

    /**
     * 创建书签
     */
    @Transactional
    public Bookmark createBookmark(User user, Long bookId, CreateBookmarkRequest request) {
        Book book = findBook(user, bookId);

        Bookmark bookmark = Bookmark.builder()
                .user(user)
                .book(book)
                .title(request.getTitle() != null ? request.getTitle() : "书签")
                .chapter(request.getChapter())
                .cfi(request.getCfi())
                .scrollPosition(request.getScrollPosition())
                .page(request.getPage() != null ? request.getPage() : 1)
                .build();

        return bookmarkRepository.save(bookmark);
    }

    /**
     * 删除书签
     */
    @Transactional
    public void deleteBookmark(User user, Long bookId, Long bookmarkId) {
        Book book = findBook(user, bookId);
        Bookmark bookmark = bookmarkRepository.findById(bookmarkId)
                .filter(b -> b.getUser().getId().equals(user.getId()))
                .filter(b -> b.getBook().getId().equals(bookId))
                .orElseThrow(() -> new ResourceNotFoundException("书签", bookmarkId));

        bookmarkRepository.delete(bookmark);
    }

    private Book findBook(User user, Long bookId) {
        return bookRepository.findById(bookId)
                .filter(b -> b.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("书籍", bookId));
    }
}
