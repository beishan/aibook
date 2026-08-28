package com.aibook.service;

import com.aibook.dto.BookDTO;
import com.aibook.dto.TagDTO;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.Category;
import com.aibook.model.entity.OperationLog;
import com.aibook.model.entity.Tag;
import com.aibook.model.entity.User;
import com.aibook.repository.BookHighlightRepository;
import com.aibook.repository.BookListRepository;
import com.aibook.repository.BookRepository;
import com.aibook.repository.BookmarkRepository;
import com.aibook.repository.ReadingProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 书籍服务
 */
@Service
@RequiredArgsConstructor
public class BookService {

    private final BookRepository bookRepository;
    private final ReadingProgressRepository readingProgressRepository;
    private final BookmarkRepository bookmarkRepository;
    private final BookHighlightRepository bookHighlightRepository;
    private final BookListRepository bookListRepository;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final AuthorService authorService;
    private final OperationLogService operationLogService;

    /**
     * 获取用户书籍列表
     */
    public Page<BookDTO> getBooks(User user, Pageable pageable) {
        Page<Book> books = bookRepository.findByUserAndDeletedAtIsNull(user, pageable);
        return books.map(this::convertToDTO);
    }

    /**
     * 根据格式筛选书籍
     */
    public Page<BookDTO> getBooksByFormat(User user, String format, Pageable pageable) {
        Page<Book> books = bookRepository.findByUserAndFormatAndDeletedAtIsNull(
                user, format, pageable);
        return books.map(this::convertToDTO);
    }

    /**
     * 根据阅读状态筛选书籍
     */
    public Page<BookDTO> getBooksByStatus(User user, Book.ReadingStatus status, Pageable pageable) {
        Page<Book> books = bookRepository.findByUserAndReadingStatusAndDeletedAtIsNull(
                user, status, pageable);
        return books.map(this::convertToDTO);
    }

    /**
     * 获取收藏书籍
     */
    public Page<BookDTO> getFavoriteBooks(User user, Pageable pageable) {
        Page<Book> books = bookRepository.findByUserAndIsFavoriteAndDeletedAtIsNull(
                user, true, pageable);
        return books.map(this::convertToDTO);
    }

    /**
     * 获取想读书籍
     */
    public Page<BookDTO> getWantedBooks(User user, Pageable pageable) {
        Page<Book> books = bookRepository.findByUserAndIsWantedAndDeletedAtIsNull(
                user, true, pageable);
        return books.map(this::convertToDTO);
    }

    /**
     * 根据分类筛选书籍
     */
    public Page<BookDTO> getBooksByCategory(
            User user, Long categoryId, boolean includeChildren, Pageable pageable) {
        categoryService.getOwnedCategory(categoryId, user);
        Page<Book> books = includeChildren
                ? bookRepository.findByUserAndCategoryIdInAndDeletedAtIsNull(
                        user, categoryService.getCategoryAndDescendantIds(categoryId, user), pageable)
                : bookRepository.findByUserAndCategoryIdAndDeletedAtIsNull(
                        user, categoryId, pageable);
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
        Book book = bookRepository.findByIdAndUserAndDeletedAtIsNull(id, user)
                .orElseThrow(() -> new RuntimeException("书籍不存在"));
        return convertToDTO(book);
    }

    /**
     * 获取书籍实体（用于刮削等操作）
     */
    public Book getBookEntity(Long id, User user) {
        return bookRepository.findByIdAndUserAndDeletedAtIsNull(id, user)
                .orElseThrow(() -> new RuntimeException("书籍不存在"));
    }

    /**
     * 获取用户所有书籍
     */
    public List<BookDTO> getAllBooks(User user) {
        List<Book> books = bookRepository.findByUserAndDeletedAtIsNull(user);
        return books.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 更新书籍收藏状态
     */
    @Transactional
    public BookDTO toggleFavorite(Long id, User user) {
        Book book = getBookEntity(id, user);

        book.setIsFavorite(!Boolean.TRUE.equals(book.getIsFavorite()));
        bookRepository.save(book);
        return convertToDTO(book);
    }

    /**
     * 更新想读状态
     */
    @Transactional
    public BookDTO toggleWanted(Long id, User user) {
        Book book = getBookEntity(id, user);

        book.setIsWanted(!Boolean.TRUE.equals(book.getIsWanted()));
        bookRepository.save(book);
        return convertToDTO(book);
    }

    /**
     * 将书籍移入回收站。只更新数据库状态，绝不删除原始文件。
     */
    @Transactional
    public void deleteBook(Long id, User user) {
        Book book = getBookEntity(id, user);
        book.setDeletedAt(LocalDateTime.now());
        bookRepository.save(book);
        operationLogService.record(
                user, OperationLog.Action.DELETE_BOOK, book,
                "删除书籍《" + book.getTitle() + "》", "已移入回收站");
    }

    @Transactional
    public void moveBooksToTrash(List<Long> bookIds, User user) {
        List<Long> distinctIds = validateBookIds(bookIds);
        List<Book> books = bookRepository.findByIdInAndUser(distinctIds, user);
        if (books.size() != distinctIds.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "部分书籍不存在、已在回收站或无权访问");
        }
        LocalDateTime deletedAt = LocalDateTime.now();
        books.forEach(book -> book.setDeletedAt(deletedAt));
        bookRepository.saveAll(books);
        books.forEach(book -> operationLogService.record(
                user, OperationLog.Action.DELETE_BOOK, book,
                "删除书籍《" + book.getTitle() + "》", "批量操作，已移入回收站"));
    }

    @Transactional(readOnly = true)
    public Page<BookDTO> getTrash(
            User user, String keyword, Pageable pageable) {
        return bookRepository.findTrash(
                        user, keyword == null ? "" : keyword.trim(), pageable)
                .map(this::convertToDTO);
    }

    @Transactional(readOnly = true)
    public long getTrashCount(User user) {
        return bookRepository.countByUserAndDeletedAtIsNotNullAndPurgedAtIsNull(user);
    }

    @Transactional
    public List<BookDTO> restoreBooks(List<Long> bookIds, User user) {
        List<Book> books = getOwnedTrashBooks(bookIds, user);
        books.forEach(book -> book.setDeletedAt(null));
        List<Book> restoredBooks = bookRepository.saveAll(books);
        restoredBooks.forEach(book -> operationLogService.record(
                user, OperationLog.Action.RESTORE_BOOK, book,
                "恢复书籍《" + book.getTitle() + "》", "已从回收站恢复"));
        return restoredBooks.stream()
                .map(this::convertToDTO)
                .toList();
    }

    /**
     * 从回收站永久移除。清理业务关联并保留防重复导入墓碑，
     * 绝不删除 filePath 指向的原始文件。
     */
    @Transactional
    public void permanentlyDeleteBooks(List<Long> bookIds, User user) {
        List<Book> books = getOwnedTrashBooks(bookIds, user);
        books.forEach(book -> {
            operationLogService.record(
                    user, OperationLog.Action.PERMANENTLY_DELETE_BOOK, book,
                    "永久删除书籍《" + book.getTitle() + "》", "原始文件保留");
            purgeDatabaseRecordOnly(book);
        });
    }

    /**
     * 清空当前用户回收站。清理业务关联并保留防重复导入墓碑，
     * 不删除原始文件。
     */
    @Transactional
    public void emptyTrash(User user) {
        bookRepository.findByUserAndDeletedAtIsNotNullAndPurgedAtIsNull(user)
                .forEach(book -> {
                    operationLogService.record(
                            user, OperationLog.Action.PERMANENTLY_DELETE_BOOK, book,
                            "永久删除书籍《" + book.getTitle() + "》", "清空回收站，原始文件保留");
                    purgeDatabaseRecordOnly(book);
                });
    }

    /** 自动清理超过保留期限的回收站记录，原始文件始终保留。 */
    @Transactional
    public int purgeExpiredTrash(User user, LocalDateTime deletedBefore) {
        List<Book> books = bookRepository
                .findByUserAndDeletedAtBeforeAndPurgedAtIsNull(user, deletedBefore);
        books.forEach(book -> {
            operationLogService.record(
                    user, OperationLog.Action.PERMANENTLY_DELETE_BOOK, book,
                    "永久删除书籍《" + book.getTitle() + "》", "回收站到期自动清理，原始文件保留");
            purgeDatabaseRecordOnly(book);
        });
        return books.size();
    }

    private List<Book> getOwnedTrashBooks(List<Long> bookIds, User user) {
        List<Long> distinctIds = validateBookIds(bookIds);
        List<Book> books = bookRepository.findTrashByIds(distinctIds, user);
        if (books.size() != distinctIds.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "部分书籍不在回收站或无权访问");
        }
        return books;
    }

    private List<Long> validateBookIds(List<Long> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "书籍ID列表不能为空");
        }
        return bookIds.stream().distinct().toList();
    }

    private void purgeDatabaseRecordOnly(Book book) {
        readingProgressRepository.deleteByBook(book);
        bookmarkRepository.deleteByBook(book);
        bookHighlightRepository.deleteByBook(book);
        bookListRepository.deleteBookAssociations(book.getId());
        book.setTags(new java.util.LinkedHashSet<>());
        book.setCategory(null);
        book.setIsFavorite(false);
        book.setIsWanted(false);
        book.setOnShelf(false);
        book.setShelfGroup(null);
        book.setShelfAddedAt(null);
        book.setShelfSortOrder(null);
        book.setNotes(null);
        book.setChapterInfo(null);
        book.setPurgedAt(LocalDateTime.now());
        bookRepository.save(book);
    }

    /**
     * 更新书籍元数据
     */
    @Transactional
    public BookDTO updateBookMetadata(Long id, BookDTO bookDTO, User user) {
        Book book = getBookEntity(id, user);

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
        authorService.synchronizeBook(book);
        return convertToDTO(book);
    }

    /**
     * 更新阅读状态
     */
    @Transactional
    public BookDTO updateReadingStatus(Long id, Book.ReadingStatus status, User user) {
        Book book = getBookEntity(id, user);

        book.setReadingStatus(status);
        bookRepository.save(book);
        return convertToDTO(book);
    }

    /**
     * 设置或清除单本书籍分类。
     */
    @Transactional
    public BookDTO updateBookCategory(Long id, Long categoryId, User user) {
        Book book = getBookEntity(id, user);
        Category category = categoryId == null
                ? null
                : categoryService.getOwnedCategory(categoryId, user);
        book.setCategory(category);
        return convertToDTO(bookRepository.save(book));
    }

    /**
     * 批量设置或清除书籍分类。
     */
    @Transactional
    public List<BookDTO> updateBookCategories(
            List<Long> bookIds, Long categoryId, User user) {
        if (bookIds == null || bookIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "书籍ID列表不能为空");
        }

        Category category = categoryId == null
                ? null
                : categoryService.getOwnedCategory(categoryId, user);
        List<Book> books = bookRepository.findByIdInAndUser(bookIds, user);
        if (books.size() != bookIds.stream().distinct().count()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "部分书籍不存在或无权访问");
        }

        books.forEach(book -> book.setCategory(category));
        return bookRepository.saveAll(books).stream()
                .map(this::convertToDTO)
                .toList();
    }

    /**
     * 替换单本书籍的全部标签。
     */
    @Transactional
    public BookDTO updateBookTags(Long id, List<Long> tagIds, User user) {
        Book book = getBookEntity(id, user);
        book.setTags(tagService.getOwnedTags(tagIds, user));
        return convertToDTO(bookRepository.save(book));
    }

    /**
     * 批量添加、移除或替换书籍标签。
     */
    @Transactional
    public List<BookDTO> updateBookTags(
            List<Long> bookIds, List<Long> tagIds, String mode, User user) {
        if (bookIds == null || bookIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "书籍ID列表不能为空");
        }

        List<Long> distinctBookIds = bookIds.stream().distinct().toList();
        List<Book> books = bookRepository.findByIdInAndUser(distinctBookIds, user);
        if (books.size() != distinctBookIds.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "部分书籍不存在或无权访问");
        }

        Set<Tag> tags = tagService.getOwnedTags(tagIds, user);
        String operation = mode == null ? "ADD" : mode.toUpperCase(Locale.ROOT);
        if (!Set.of("ADD", "REMOVE", "REPLACE").contains(operation)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "标签操作方式必须是 ADD、REMOVE 或 REPLACE");
        }

        books.forEach(book -> {
            switch (operation) {
                case "REMOVE" -> book.getTags().removeAll(tags);
                case "REPLACE" -> book.setTags(new java.util.LinkedHashSet<>(tags));
                default -> book.getTags().addAll(tags);
            }
        });
        return bookRepository.saveAll(books).stream()
                .map(this::convertToDTO)
                .toList();
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
                .sourceType(book.getSourceType() == null ? null : book.getSourceType().name())
                .sourcePath(book.getSourceType() == Book.SourceType.DIRECTORY_SCAN
                        ? book.getFilePath()
                        : null)
                .fileSize(book.getFileSize())
                .language(book.getLanguage())
                .rating(book.getRating())
                .readingStatus(book.getReadingStatus().name())
                .categoryId(book.getCategory() != null ? book.getCategory().getId() : null)
                .categoryName(book.getCategory() != null ? book.getCategory().getName() : null)
                .categoryPath(buildCategoryPath(book.getCategory()))
                .tags(book.getTags().stream()
                        .map(tag -> TagDTO.builder()
                                .id(tag.getId())
                                .name(tag.getName())
                                .color(tag.getColor())
                                .build())
                        .sorted(java.util.Comparator.comparing(TagDTO::getName))
                        .toList())
                .tagNames(book.getTags().stream()
                        .map(Tag::getName)
                        .sorted()
                        .collect(Collectors.toList()))
                .isFavorite(book.getIsFavorite())
                .isWanted(book.getIsWanted())
                .onShelf(Boolean.TRUE.equals(book.getOnShelf()))
                .shelfGroupId(book.getShelfGroup() == null ? null : book.getShelfGroup().getId())
                .shelfAddedAt(book.getShelfAddedAt())
                .shelfSortOrder(book.getShelfSortOrder())
                .notes(book.getNotes())
                .chapterInfo(book.getChapterInfo())
                .chapterCount(book.getChapterCount())
                .deletedAt(book.getDeletedAt())
                .createdAt(book.getCreatedAt())
                .updatedAt(book.getUpdatedAt())
                .build();
    }

    private String buildCategoryPath(Category category) {
        if (category == null) {
            return null;
        }
        List<String> names = new java.util.ArrayList<>();
        Category current = category;
        while (current != null) {
            names.add(0, current.getName());
            current = current.getParent();
        }
        return String.join(" / ", names);
    }
}
