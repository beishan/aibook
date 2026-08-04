package com.aibook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibook.dto.BookDTO;
import com.aibook.dto.ShelfBookOrderRequest;
import com.aibook.dto.ShelfOverviewDTO;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.OperationLog;
import com.aibook.model.entity.ShelfGroup;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.ShelfGroupRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ShelfServiceTest {

    private ShelfGroupRepository groupRepository;
    private BookRepository bookRepository;
    private BookService bookService;
    private OperationLogService operationLogService;
    private ShelfService service;
    private User user;

    @BeforeEach
    void setUp() {
        groupRepository = mock(ShelfGroupRepository.class);
        bookRepository = mock(BookRepository.class);
        bookService = mock(BookService.class);
        operationLogService = mock(OperationLogService.class);
        service = new ShelfService(groupRepository, bookRepository, bookService, operationLogService);
        user = User.builder().id(1L).username("reader").build();
    }

    @Test
    void addsBookAtTopOfUngroupedShelfAndRecordsOperation() {
        Book existing = shelfBook(2L, null, 0);
        Book added = Book.builder()
                .id(3L)
                .title("新加入")
                .format("epub")
                .filePath("/books/new.epub")
                .user(user)
                .build();
        BookDTO dto = BookDTO.builder().id(3L).onShelf(true).build();
        when(bookRepository.findByIdAndUserAndDeletedAtIsNull(3L, user))
                .thenReturn(Optional.of(added));
        when(bookRepository.findShelfBooks(user)).thenReturn(List.of(existing));
        when(bookService.convertToDTO(added)).thenReturn(dto);

        BookDTO result = service.addBook(3L, null, user);

        assertThat(result).isSameAs(dto);
        assertThat(added.getOnShelf()).isTrue();
        assertThat(added.getShelfAddedAt()).isNotNull();
        assertThat(added.getShelfSortOrder()).isEqualTo(-1);
        assertThat(added.getShelfGroup()).isNull();
        verify(bookRepository).save(added);
        verify(operationLogService).record(
                user,
                OperationLog.Action.ADD_TO_SHELF,
                added,
                "将书籍《新加入》加入书架",
                "未分组");
    }

    @Test
    void buildsOverviewWithCustomAndUngroupedBooks() {
        ShelfGroup group = ShelfGroup.builder()
                .id(8L)
                .user(user)
                .name("技术")
                .icon("💡")
                .color("#123456")
                .sortOrder(0)
                .build();
        Book ungrouped = shelfBook(1L, null, 0);
        Book grouped = shelfBook(2L, group, 0);
        when(groupRepository.findByUserOrderBySortOrderAscCreatedAtAsc(user))
                .thenReturn(List.of(group));
        when(bookRepository.findShelfBooks(user)).thenReturn(List.of(ungrouped, grouped));
        when(bookService.convertToDTO(ungrouped)).thenReturn(BookDTO.builder().id(1L).build());
        when(bookService.convertToDTO(grouped)).thenReturn(BookDTO.builder().id(2L).build());

        ShelfOverviewDTO result = service.getShelf(user);

        assertThat(result.getTotalBooks()).isEqualTo(2);
        assertThat(result.getUngroupedBooks()).extracting(BookDTO::getId).containsExactly(1L);
        assertThat(result.getGroups()).hasSize(1);
        assertThat(result.getGroups().getFirst().getBooks())
                .extracting(BookDTO::getId)
                .containsExactly(2L);
    }

    @Test
    void rejectsBookOrderThatDoesNotContainTheWholeSelectedGroup() {
        Book first = shelfBook(1L, null, 0);
        Book second = shelfBook(2L, null, 1);
        when(bookRepository.findShelfBooks(user)).thenReturn(List.of(first, second));
        ShelfBookOrderRequest request = new ShelfBookOrderRequest();
        request.setBookIds(List.of(1L));

        assertThatThrownBy(() -> service.reorderBooks(request, user))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("排序列表与当前内容不一致");
    }

    @Test
    void deletingGroupAlsoClearsReferencesFromBooksOutsideVisibleShelfQuery() {
        ShelfGroup group = ShelfGroup.builder().id(8L).user(user).name("技术").build();
        Book ungrouped = shelfBook(1L, null, 3);
        Book hiddenOrDeleted = shelfBook(2L, group, 0);
        hiddenOrDeleted.setDeletedAt(LocalDateTime.now());
        when(groupRepository.findByIdAndUser(8L, user)).thenReturn(Optional.of(group));
        when(bookRepository.findByUserAndOnShelfTrue(user)).thenReturn(List.of(ungrouped, hiddenOrDeleted));
        when(bookRepository.findByUserAndShelfGroup(user, group)).thenReturn(List.of(hiddenOrDeleted));

        service.deleteGroup(8L, user);

        assertThat(hiddenOrDeleted.getShelfGroup()).isNull();
        assertThat(hiddenOrDeleted.getShelfSortOrder()).isEqualTo(4);
        verify(bookRepository).saveAll(List.of(hiddenOrDeleted));
        verify(groupRepository).delete(group);
    }

    private Book shelfBook(Long id, ShelfGroup group, int order) {
        return Book.builder()
                .id(id)
                .title("书籍" + id)
                .format("epub")
                .filePath("/books/" + id + ".epub")
                .user(user)
                .onShelf(true)
                .shelfGroup(group)
                .shelfSortOrder(order)
                .shelfAddedAt(LocalDateTime.now())
                .build();
    }
}
