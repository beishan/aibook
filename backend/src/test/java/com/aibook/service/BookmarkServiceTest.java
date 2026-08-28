package com.aibook.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aibook.dto.CreateBookmarkRequest;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.Bookmark;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.BookmarkRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BookmarkServiceTest {

    @Test
    void createBookmarkPersistsExcerptAndOneBasedChapterIndex() {
        BookmarkRepository bookmarkRepository = mock(BookmarkRepository.class);
        BookRepository bookRepository = mock(BookRepository.class);
        BookmarkService service = new BookmarkService(bookmarkRepository, bookRepository);
        User user = User.builder().id(7L).username("reader").build();
        Book book = Book.builder().id(11L).title("测试书籍").user(user).build();
        CreateBookmarkRequest request = CreateBookmarkRequest.builder()
                .title("")
                .excerpt("这是书签所在位置附近的正文")
                .chapter("第三章 新的开始")
                .chapterIndex(3)
                .build();

        when(bookRepository.findByIdAndUserAndDeletedAtIsNull(11L, user))
                .thenReturn(Optional.of(book));
        when(bookmarkRepository.save(any(Bookmark.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Bookmark result = service.createBookmark(user, 11L, request);

        assertEquals("这是书签所在位置附近的正文", result.getExcerpt());
        assertEquals("第三章 新的开始", result.getChapter());
        assertEquals(3, result.getChapterIndex());
    }
}
