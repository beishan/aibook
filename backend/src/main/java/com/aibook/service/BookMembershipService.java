package com.aibook.service;

import com.aibook.dto.BookDTO;
import com.aibook.dto.BookMembershipRequest;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookList;
import com.aibook.model.entity.User;
import com.aibook.repository.BookListRepository;
import com.aibook.repository.BookRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class BookMembershipService {

    private final BookRepository bookRepository;
    private final BookListRepository bookListRepository;
    private final BookService bookService;
    private final ShelfService shelfService;

    @Transactional
    public List<BookDTO> add(BookMembershipRequest request, User user) {
        List<Long> ids = request.bookIds().stream().distinct().toList();
        List<Book> books = bookRepository.findByIdInAndUser(ids, user);
        if (books.size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "部分书籍不存在或无权访问，请刷新后重试");
        }

        switch (request.action()) {
            case FAVORITE -> {
                books.forEach(book -> book.setIsFavorite(true));
                bookRepository.saveAll(books);
            }
            case SHELF -> {
                // 复用入架日志、排序与幂等规则，已入架书籍保留原分组。
                return books.stream().map(book -> shelfService.addBook(book.getId(), null, user)).toList();
            }
            case BOOK_LIST -> addToLists(request.listIds(), books, user);
        }
        return books.stream().map(bookService::convertToDTO).toList();
    }

    private void addToLists(List<Long> listIds, List<Book> books, User user) {
        if (listIds == null || listIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择目标书单");
        }
        List<Long> ids = listIds.stream().distinct().toList();
        List<BookList> lists = bookListRepository.findByIdInAndUser(ids, user);
        if (lists.size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "部分书单不存在或无权访问，请重新选择");
        }
        for (BookList list : lists) {
            Set<Long> existingIds = new HashSet<>();
            list.getBooks().forEach(book -> existingIds.add(book.getId()));
            for (Book book : books) {
                if (existingIds.add(book.getId())) list.getBooks().add(book);
            }
        }
        bookListRepository.saveAll(lists);
    }
}
