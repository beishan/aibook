package com.aibook.service;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookList;
import com.aibook.model.entity.User;
import com.aibook.repository.BookListRepository;
import com.aibook.repository.BookRepository;
import com.aibook.repository.ReadingProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 书单服务
 */
@Service
@RequiredArgsConstructor
public class BookListService {

    private final BookListRepository bookListRepository;
    private final BookRepository bookRepository;
    private final ReadingProgressRepository readingProgressRepository;

    public record BookCard(Long id, String title, String author, String format, String coverUrl,
            Integer totalProgress, Boolean isFavorite, Boolean isWanted, Boolean onShelf) { }
    public record BookListPage(Long id, String name, String description, Page<BookCard> books) { }

    @Transactional(readOnly = true)
    public BookListPage getBookListPage(Long id, User user, int page, int size) {
        BookList list = bookListRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "书单不存在"));
        Page<Book> pageOfBooks = bookRepository.findBookListPage(id, user,
                PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 100)));
        List<Long> bookIds = pageOfBooks.getContent().stream().map(Book::getId).toList();
        Map<Long, Integer> progressByBook = bookIds.isEmpty() ? Map.of()
                : readingProgressRepository.findByUserAndBookIdIn(user, bookIds).stream()
                        .filter(progress -> progress.getTotalProgress() != null)
                        .collect(Collectors.toMap(progress -> progress.getBook().getId(),
                                progress -> progress.getTotalProgress()));
        Page<BookCard> books = pageOfBooks
                .map(book -> new BookCard(book.getId(), book.getTitle(), book.getAuthor(),
                        book.getFormat(), book.getCoverUrl(), progressByBook.get(book.getId()),
                        book.getIsFavorite(), book.getIsWanted(), book.getOnShelf()));
        return new BookListPage(list.getId(), list.getName(), list.getDescription(), books);
    }

    /**
     * 获取用户所有书单
     */
    @Transactional(readOnly = true)
    public List<BookList> getBookLists(User user) {
        List<BookList> bookLists = bookListRepository.findByUser(user);
        // 对话框需要根据成员书籍判断“已加入”状态，必须在事务内加载懒集合。
        bookLists.forEach(bookList -> bookList.getBooks().size());
        return bookLists;
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

        bookListRepository.deleteCrawlerBookAssociations(bookList.getId());
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

        Book book = bookRepository.findByIdAndUserAndDeletedAtIsNull(bookId, user)
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
