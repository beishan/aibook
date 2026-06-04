package com.aibook.service;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookList;
import com.aibook.model.entity.User;
import com.aibook.repository.BookListRepository;
import com.aibook.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 书单服务
 */
@Service
@RequiredArgsConstructor
public class BookListService {

    private final BookListRepository bookListRepository;
    private final BookRepository bookRepository;

    /**
     * 获取用户所有书单
     */
    @Transactional(readOnly = true)
    public List<BookList> getBookLists(User user) {
        return bookListRepository.findByUser(user);
    }

    /**
     * 获取书单详情
     */
    @Transactional(readOnly = true)
    public BookList getBookList(Long id, User user) {
        BookList bookList = bookListRepository.findById(id)
                .filter(bl -> bl.getUser().equals(user))
                .orElseThrow(() -> new RuntimeException("书单不存在"));
        // 强制加载懒加载的 books 集合
        bookList.getBooks().size();
        return bookList;
    }

    /**
     * 创建书单
     */
    @Transactional
    public BookList createBookList(User user, String name, String description) {
        BookList existing = bookListRepository.findByNameAndUser(name, user);
        if (existing != null) {
            throw new RuntimeException("书单名称已存在");
        }

        BookList bookList = BookList.builder()
                .name(name)
                .description(description)
                .user(user)
                .build();

        return bookListRepository.save(bookList);
    }

    /**
     * 更新书单
     */
    @Transactional
    public BookList updateBookList(Long id, String name, String description, User user) {
        BookList bookList = bookListRepository.findById(id)
                .filter(bl -> bl.getUser().equals(user))
                .orElseThrow(() -> new RuntimeException("书单不存在"));

        bookList.setName(name);
        bookList.setDescription(description);

        return bookListRepository.save(bookList);
    }

    /**
     * 删除书单
     */
    @Transactional
    public void deleteBookList(Long id, User user) {
        BookList bookList = bookListRepository.findById(id)
                .filter(bl -> bl.getUser().equals(user))
                .orElseThrow(() -> new RuntimeException("书单不存在"));

        bookListRepository.delete(bookList);
    }

    /**
     * 向书单添加书籍
     */
    @Transactional
    public BookList addBookToList(Long listId, Long bookId, User user) {
        BookList bookList = bookListRepository.findById(listId)
                .filter(bl -> bl.getUser().equals(user))
                .orElseThrow(() -> new RuntimeException("书单不存在"));

        Book book = bookRepository.findById(bookId)
                .filter(b -> b.getUser().equals(user))
                .orElseThrow(() -> new RuntimeException("书籍不存在"));

        if (!bookList.getBooks().contains(book)) {
            bookList.getBooks().add(book);
            bookListRepository.save(bookList);
        }

        return bookList;
    }

    /**
     * 从书单移除书籍
     */
    @Transactional
    public BookList removeBookFromList(Long listId, Long bookId, User user) {
        BookList bookList = bookListRepository.findById(listId)
                .filter(bl -> bl.getUser().equals(user))
                .orElseThrow(() -> new RuntimeException("书单不存在"));

        bookList.getBooks().removeIf(book -> book.getId().equals(bookId));
        bookListRepository.save(bookList);

        return bookList;
    }
}
