package com.aibook.service;

import com.aibook.dto.BookDTO;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 书籍服务
 */
@Service
@RequiredArgsConstructor
public class BookService {

    private final BookRepository bookRepository;

    /**
     * 获取用户书籍列表
     */
    public Page<BookDTO> getBooks(User user, Pageable pageable) {
        Page<Book> books = bookRepository.findByUser(user, pageable);
        return books.map(this::convertToDTO);
    }

    /**
     * 根据格式筛选书籍
     */
    public Page<BookDTO> getBooksByFormat(User user, String format, Pageable pageable) {
        Page<Book> books = bookRepository.findByUserAndFormat(user, format, pageable);
        return books.map(this::convertToDTO);
    }

    /**
     * 根据阅读状态筛选书籍
     */
    public Page<BookDTO> getBooksByStatus(User user, Book.ReadingStatus status, Pageable pageable) {
        Page<Book> books = bookRepository.findByUserAndReadingStatus(user, status, pageable);
        return books.map(this::convertToDTO);
    }

    /**
     * 获取收藏书籍
     */
    public Page<BookDTO> getFavoriteBooks(User user, Pageable pageable) {
        Page<Book> books = bookRepository.findByUserAndIsFavorite(user, true, pageable);
        return books.map(this::convertToDTO);
    }

    /**
     * 获取想读书籍
     */
    public Page<BookDTO> getWantedBooks(User user, Pageable pageable) {
        Page<Book> books = bookRepository.findByUserAndIsWanted(user, true, pageable);
        return books.map(this::convertToDTO);
    }

    /**
     * 根据分类筛选书籍
     */
    public Page<BookDTO> getBooksByCategory(User user, Long categoryId, Pageable pageable) {
        Page<Book> books = bookRepository.findByUserAndCategoryId(user, categoryId, pageable);
        return books.map(this::convertToDTO);
    }

    /**
     * 根据标签筛选书籍
     */
    public Page<BookDTO> getBooksByTag(User user, Long tagId, Pageable pageable) {
        Page<Book> books = bookRepository.findByUserAndTagId(user, tagId, pageable);
        return books.map(this::convertToDTO);
    }

    /**
     * 搜索书籍
     */
    public Page<BookDTO> searchBooks(User user, String keyword, Pageable pageable) {
        Page<Book> books = bookRepository.searchByKeyword(user, keyword, pageable);
        return books.map(this::convertToDTO);
    }

    /**
     * 获取书籍详情
     */
    public BookDTO getBookById(Long id, User user) {
        Book book = bookRepository.findById(id)
                .filter(b -> b.getUser().equals(user))
                .orElseThrow(() -> new RuntimeException("书籍不存在"));
        return convertToDTO(book);
    }

    /**
     * 获取书籍实体（用于刮削等操作）
     */
    public Book getBookEntity(Long id, User user) {
        return bookRepository.findById(id)
                .filter(b -> b.getUser().equals(user))
                .orElseThrow(() -> new RuntimeException("书籍不存在"));
    }

    /**
     * 获取用户所有书籍
     */
    public List<BookDTO> getAllBooks(User user) {
        List<Book> books = bookRepository.findByUser(user);
        return books.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 更新书籍收藏状态
     */
    @Transactional
    public BookDTO toggleFavorite(Long id, User user) {
        Book book = bookRepository.findById(id)
                .filter(b -> b.getUser().equals(user))
                .orElseThrow(() -> new RuntimeException("书籍不存在"));

        book.setIsFavorite(!Boolean.TRUE.equals(book.getIsFavorite()));
        bookRepository.save(book);
        return convertToDTO(book);
    }

    /**
     * 更新想读状态
     */
    @Transactional
    public BookDTO toggleWanted(Long id, User user) {
        Book book = bookRepository.findById(id)
                .filter(b -> b.getUser().equals(user))
                .orElseThrow(() -> new RuntimeException("书籍不存在"));

        book.setIsWanted(!Boolean.TRUE.equals(book.getIsWanted()));
        bookRepository.save(book);
        return convertToDTO(book);
    }

    /**
     * 删除书籍
     */
    @Transactional
    public void deleteBook(Long id, User user) {
        Book book = bookRepository.findById(id)
                .filter(b -> b.getUser().equals(user))
                .orElseThrow(() -> new RuntimeException("书籍不存在"));

        bookRepository.delete(book);
    }

    /**
     * 更新书籍元数据
     */
    @Transactional
    public BookDTO updateBookMetadata(Long id, BookDTO bookDTO, User user) {
        Book book = bookRepository.findById(id)
                .filter(b -> b.getUser().equals(user))
                .orElseThrow(() -> new RuntimeException("书籍不存在"));

        if (bookDTO.getTitle() != null) book.setTitle(bookDTO.getTitle());
        if (bookDTO.getAuthor() != null) book.setAuthor(bookDTO.getAuthor());
        if (bookDTO.getIsbn() != null) book.setIsbn(bookDTO.getIsbn());
        if (bookDTO.getPublisher() != null) book.setPublisher(bookDTO.getPublisher());
        if (bookDTO.getPublishDate() != null) book.setPublishDate(bookDTO.getPublishDate());
        if (bookDTO.getDescription() != null) book.setDescription(bookDTO.getDescription());
        if (bookDTO.getCoverUrl() != null) book.setCoverUrl(bookDTO.getCoverUrl());
        if (bookDTO.getLanguage() != null) book.setLanguage(bookDTO.getLanguage());
        if (bookDTO.getRating() != null) book.setRating(bookDTO.getRating());
        if (bookDTO.getNotes() != null) book.setNotes(bookDTO.getNotes());

        bookRepository.save(book);
        return convertToDTO(book);
    }

    /**
     * 更新阅读状态
     */
    @Transactional
    public BookDTO updateReadingStatus(Long id, Book.ReadingStatus status, User user) {
        Book book = bookRepository.findById(id)
                .filter(b -> b.getUser().equals(user))
                .orElseThrow(() -> new RuntimeException("书籍不存在"));

        book.setReadingStatus(status);
        bookRepository.save(book);
        return convertToDTO(book);
    }

    /**
     * 转换为 DTO
     */
    public BookDTO convertToDTO(Book book) {
        return BookDTO.builder()
                .id(book.getId())
                .title(book.getTitle())
                .author(book.getAuthor())
                .isbn(book.getIsbn())
                .publisher(book.getPublisher())
                .publishDate(book.getPublishDate())
                .description(book.getDescription())
                .coverUrl(book.getCoverUrl())
                .format(book.getFormat())
                .filePath(book.getFilePath())
                .fileSize(book.getFileSize())
                .language(book.getLanguage())
                .rating(book.getRating())
                .readingStatus(book.getReadingStatus().name())
                .categoryName(book.getCategory() != null ? book.getCategory().getName() : null)
                .tagNames(book.getTags().stream().map(tag -> tag.getName()).collect(Collectors.toList()))
                .isFavorite(book.getIsFavorite())
                .isWanted(book.getIsWanted())
                .notes(book.getNotes())
                .createdAt(book.getCreatedAt())
                .updatedAt(book.getUpdatedAt())
                .build();
    }
}
