package com.aibook.service;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.Category;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.CategoryRepository;
import com.aibook.repository.UserRepository;
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

    /**
     * 使用当前事务创建的实体引用，避免跨线程共享 Hibernate 代理。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Book save(Book book, Long userId, Long categoryId) {
        User user = userRepository.getReferenceById(userId);
        Category category = categoryId == null
                ? null
                : categoryRepository.getReferenceById(categoryId);

        book.setUser(user);
        book.setCategory(category);
        return bookRepository.save(book);
    }
}
