package com.aibook.service;

import com.aibook.dto.BookDTO;
import com.aibook.dto.ShelfBookOrderRequest;
import com.aibook.dto.ShelfGroupDTO;
import com.aibook.dto.ShelfGroupOrderRequest;
import com.aibook.dto.ShelfGroupRequest;
import com.aibook.dto.ShelfOverviewDTO;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.OperationLog;
import com.aibook.model.entity.ShelfGroup;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.ShelfGroupRepository;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ShelfService {

    private static final String DEFAULT_ICON = "📁";
    private static final String DEFAULT_COLOR = "#4f8cff";

    private final ShelfGroupRepository shelfGroupRepository;
    private final BookRepository bookRepository;
    private final BookService bookService;
    private final OperationLogService operationLogService;

    @Transactional(readOnly = true)
    public ShelfOverviewDTO getShelf(User user) {
        List<Book> books = bookRepository.findShelfBooks(user);
        List<ShelfGroupDTO> groups = shelfGroupRepository
                .findByUserOrderBySortOrderAscCreatedAtAsc(user).stream()
                .map(group -> toDTO(group, books.stream()
                        .filter(book -> sameGroup(book, group.getId()))
                        .map(bookService::convertToDTO)
                        .toList()))
                .toList();
        List<BookDTO> ungrouped = books.stream()
                .filter(book -> book.getShelfGroup() == null)
                .map(bookService::convertToDTO)
                .toList();
        return ShelfOverviewDTO.builder()
                .ungroupedBooks(ungrouped)
                .groups(groups)
                .totalBooks(books.size())
                .build();
    }

    @Transactional
    public BookDTO addBook(Long bookId, Long groupId, User user) {
        Book book = ownedBook(bookId, user);
        ShelfGroup group = ownedGroup(groupId, user);
        if (!Boolean.TRUE.equals(book.getOnShelf())) {
            book.setOnShelf(true);
            book.setShelfAddedAt(LocalDateTime.now());
            book.setShelfGroup(group);
            book.setShelfSortOrder(nextTopOrder(user, groupId));
            bookRepository.save(book);
            operationLogService.record(
                    user,
                    OperationLog.Action.ADD_TO_SHELF,
                    book,
                    "将书籍《" + book.getTitle() + "》加入书架",
                    group == null ? "未分组" : "分组：" + group.getName());
        }
        return bookService.convertToDTO(book);
    }

    @Transactional
    public BookDTO removeBook(Long bookId, User user) {
        Book book = ownedBook(bookId, user);
        if (Boolean.TRUE.equals(book.getOnShelf())) {
            book.setOnShelf(false);
            book.setShelfGroup(null);
            book.setShelfAddedAt(null);
            book.setShelfSortOrder(null);
            bookRepository.save(book);
            operationLogService.record(
                    user,
                    OperationLog.Action.REMOVE_FROM_SHELF,
                    book,
                    "将书籍《" + book.getTitle() + "》移出书架",
                    null);
        }
        return bookService.convertToDTO(book);
    }

    @Transactional
    public BookDTO moveBook(Long bookId, Long groupId, User user) {
        Book book = ownedShelfBook(bookId, user);
        ShelfGroup group = ownedGroup(groupId, user);
        if (!sameGroup(book, groupId)) {
            book.setShelfGroup(group);
            book.setShelfSortOrder(nextTopOrder(user, groupId));
            bookRepository.save(book);
        }
        return bookService.convertToDTO(book);
    }

    @Transactional
    public void reorderBooks(ShelfBookOrderRequest request, User user) {
        List<Book> targetBooks = bookRepository.findShelfBooks(user).stream()
                .filter(book -> sameGroup(book, request.getGroupId()))
                .toList();
        validateExactOrder(
                request.getBookIds(), targetBooks.stream().map(Book::getId).toList(), "书籍");
        java.util.Map<Long, Book> byId = targetBooks.stream()
                .collect(java.util.stream.Collectors.toMap(Book::getId, book -> book));
        for (int index = 0; index < request.getBookIds().size(); index++) {
            byId.get(request.getBookIds().get(index)).setShelfSortOrder(index);
        }
        bookRepository.saveAll(targetBooks);
    }

    @Transactional
    public ShelfGroupDTO createGroup(ShelfGroupRequest request, User user) {
        String name = normalizeName(request.getName());
        if (shelfGroupRepository.existsByUserAndNameIgnoreCase(user, name)) {
            throw badRequest("书架分组名称已存在");
        }
        int nextOrder = shelfGroupRepository.findByUserOrderBySortOrderAscCreatedAtAsc(user).stream()
                .map(ShelfGroup::getSortOrder)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1) + 1;
        ShelfGroup group = shelfGroupRepository.save(ShelfGroup.builder()
                .user(user)
                .name(name)
                .description(normalizeDescription(request.getDescription()))
                .icon(normalizeIcon(request.getIcon()))
                .color(normalizeColor(request.getColor()))
                .sortOrder(nextOrder)
                .build());
        return toDTO(group, List.of());
    }

    @Transactional
    public ShelfGroupDTO updateGroup(Long groupId, ShelfGroupRequest request, User user) {
        ShelfGroup group = requireGroup(groupId, user);
        String name = normalizeName(request.getName());
        if (shelfGroupRepository.existsByUserAndNameIgnoreCaseAndIdNot(user, name, groupId)) {
            throw badRequest("书架分组名称已存在");
        }
        group.setName(name);
        group.setDescription(normalizeDescription(request.getDescription()));
        group.setIcon(normalizeIcon(request.getIcon()));
        group.setColor(normalizeColor(request.getColor()));
        shelfGroupRepository.save(group);
        List<BookDTO> books = bookRepository.findShelfBooks(user).stream()
                .filter(book -> sameGroup(book, groupId))
                .map(bookService::convertToDTO)
                .toList();
        return toDTO(group, books);
    }

    @Transactional
    public void deleteGroup(Long groupId, User user) {
        ShelfGroup group = requireGroup(groupId, user);
        List<Book> shelfBooks = bookRepository.findByUserAndOnShelfTrue(user);
        List<Book> movedBooks = bookRepository.findByUserAndShelfGroup(user, group);
        int nextOrder = shelfBooks.stream()
                .filter(book -> book.getShelfGroup() == null)
                .map(Book::getShelfSortOrder)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1) + 1;
        for (Book book : movedBooks) {
            book.setShelfGroup(null);
            book.setShelfSortOrder(nextOrder++);
        }
        bookRepository.saveAll(movedBooks);
        shelfGroupRepository.delete(group);
    }

    @Transactional
    public void reorderGroups(ShelfGroupOrderRequest request, User user) {
        List<ShelfGroup> groups = shelfGroupRepository.findByUserOrderBySortOrderAscCreatedAtAsc(user);
        validateExactOrder(
                request.getGroupIds(), groups.stream().map(ShelfGroup::getId).toList(), "分组");
        java.util.Map<Long, ShelfGroup> byId = groups.stream()
                .collect(java.util.stream.Collectors.toMap(ShelfGroup::getId, group -> group));
        for (int index = 0; index < request.getGroupIds().size(); index++) {
            byId.get(request.getGroupIds().get(index)).setSortOrder(index);
        }
        shelfGroupRepository.saveAll(groups);
    }

    private int nextTopOrder(User user, Long groupId) {
        return bookRepository.findShelfBooks(user).stream()
                .filter(book -> sameGroup(book, groupId))
                .map(Book::getShelfSortOrder)
                .filter(Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(1) - 1;
    }

    private Book ownedBook(Long bookId, User user) {
        return bookRepository.findByIdAndUserAndDeletedAtIsNull(bookId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "书籍不存在"));
    }

    private Book ownedShelfBook(Long bookId, User user) {
        Book book = ownedBook(bookId, user);
        if (!Boolean.TRUE.equals(book.getOnShelf())) {
            throw badRequest("书籍尚未加入书架");
        }
        return book;
    }

    private ShelfGroup ownedGroup(Long groupId, User user) {
        return groupId == null ? null : requireGroup(groupId, user);
    }

    private ShelfGroup requireGroup(Long groupId, User user) {
        return shelfGroupRepository.findByIdAndUser(groupId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "书架分组不存在"));
    }

    private boolean sameGroup(Book book, Long groupId) {
        return Objects.equals(
                book.getShelfGroup() == null ? null : book.getShelfGroup().getId(), groupId);
    }

    private void validateExactOrder(List<Long> requested, List<Long> actual, String target) {
        if (requested == null
                || requested.size() != actual.size()
                || new HashSet<>(requested).size() != requested.size()
                || !new HashSet<>(requested).equals(new HashSet<>(actual))) {
            throw badRequest(target + "排序列表与当前内容不一致，请刷新后重试");
        }
    }

    private String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw badRequest("分组名称不能为空");
        }
        return name.trim();
    }

    private String normalizeDescription(String description) {
        return description == null || description.trim().isEmpty() ? null : description.trim();
    }

    private String normalizeIcon(String icon) {
        return icon == null || icon.trim().isEmpty() ? DEFAULT_ICON : icon.trim();
    }

    private String normalizeColor(String color) {
        return color != null && color.matches("#[0-9a-fA-F]{6}") ? color : DEFAULT_COLOR;
    }

    private ShelfGroupDTO toDTO(ShelfGroup group, List<BookDTO> books) {
        return ShelfGroupDTO.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .icon(group.getIcon())
                .color(group.getColor())
                .sortOrder(group.getSortOrder())
                .createdAt(group.getCreatedAt())
                .books(books)
                .build();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
