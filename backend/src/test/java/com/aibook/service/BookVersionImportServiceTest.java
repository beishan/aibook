package com.aibook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibook.dto.BookVersionImportRequest;
import com.aibook.dto.BookVersionImportRequest.SourceHandling;
import com.aibook.dto.BookVersionImportRequest.SourceSelection;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookVersion;
import com.aibook.model.entity.LibraryChapter;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.BookVersionRepository;
import com.aibook.repository.LibraryChapterRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

class BookVersionImportServiceTest {

    private BookRepository bookRepository;
    private BookVersionRepository versionRepository;
    private LibraryChapterRepository chapterRepository;
    private BookVersionService versionService;
    private BookVersionAggregationService aggregationService;
    private BookVersionImportService service;

    @BeforeEach
    void setUp() {
        bookRepository = mock(BookRepository.class);
        versionRepository = mock(BookVersionRepository.class);
        chapterRepository = mock(LibraryChapterRepository.class);
        versionService = mock(BookVersionService.class);
        aggregationService = mock(BookVersionAggregationService.class);
        service = new BookVersionImportService(
                bookRepository,
                versionRepository,
                chapterRepository,
                versionService,
                aggregationService);
    }

    @Test
    void listsSameTitleCandidateFirstWithPageVersionCounts() {
        User user = User.builder().id(1L).build();
        Book target = book(10L, "三体", user);
        Book sameTitle = book(11L, "三体", user);
        Book another = book(12L, "球状闪电", user);
        when(bookRepository.findVersionImportCandidates(
                any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sameTitle, another)));
        BookVersionRepository.BookVersionCount count =
                mock(BookVersionRepository.BookVersionCount.class);
        when(count.getBookId()).thenReturn(11L);
        when(count.getVersionCount()).thenReturn(3L);
        when(versionRepository.countByBookIds(List.of(11L, 12L)))
                .thenReturn(List.of(count));

        var page = service.candidates(target, user, 0, 10, "");

        assertThat(page.content()).extracting("id").containsExactly(11L, 12L);
        assertThat(page.content().get(0).sameTitle()).isTrue();
        assertThat(page.content().get(0).versionCount()).isEqualTo(3);
        assertThat(page.content().get(1).versionCount()).isEqualTo(1);
    }

    @Test
    void keepsSourceBookAndCopiesSelectedStructuredVersionAndChapters() {
        User user = User.builder().id(1L).build();
        Book target = book(10L, "三体", user);
        Book sourceBook = book(11L, "三体", user);
        BookVersion source = BookVersion.builder()
                .id(101L)
                .book(sourceBook)
                .displayName("在线章节")
                .format("structured")
                .filePath("crawler://book/11")
                .fileSize(500L)
                .primaryVersion(true)
                .chapterCount(1)
                .build();
        LibraryChapter chapter = LibraryChapter.builder()
                .bookVersion(source)
                .chapterKey("chapter-1")
                .chapterIndex(1)
                .title("第一章")
                .content("正文")
                .contentHash("hash")
                .wordCount(2)
                .build();
        when(bookRepository.findByIdInAndUser(List.of(11L), user))
                .thenReturn(List.of(sourceBook));
        when(versionRepository.findByBookOrderByPrimaryVersionDescCreatedAtAsc(sourceBook))
                .thenReturn(List.of(source));
        when(versionRepository.save(any(BookVersion.class))).thenAnswer(invocation -> {
            BookVersion saved = invocation.getArgument(0);
            saved.setId(201L);
            return saved;
        });
        when(chapterRepository.findByBookVersionOrderByChapterIndexAsc(source))
                .thenReturn(List.of(chapter));
        when(versionService.getVersions(target)).thenReturn(List.of());

        service.importVersions(target, user, new BookVersionImportRequest(
                List.of(new SourceSelection(11L, List.of(101L))), SourceHandling.KEEP));

        var versionCaptor = org.mockito.ArgumentCaptor.forClass(BookVersion.class);
        verify(versionRepository).save(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getBook()).isSameAs(target);
        assertThat(versionCaptor.getValue().getPrimaryVersion()).isFalse();
        assertThat(versionCaptor.getValue().getSourceType())
                .isEqualTo("LIBRARY_VERSION_IMPORT");
        verify(chapterRepository).saveAll(any());
        verify(aggregationService, never())
                .aggregatePairInCurrentTransaction(any(), any(), any());
    }

    @Test
    void mergesOnlyWhenEverySourceVersionIsSelected() {
        User user = User.builder().id(1L).build();
        Book target = book(10L, "三体", user);
        Book sourceBook = book(11L, "三体", user);
        BookVersion first = version(101L, sourceBook);
        BookVersion second = version(102L, sourceBook);
        when(bookRepository.findByIdInAndUser(List.of(11L), user))
                .thenReturn(List.of(sourceBook));
        when(versionRepository.findByBookOrderByPrimaryVersionDescCreatedAtAsc(sourceBook))
                .thenReturn(List.of(first, second));

        assertThrows(ResponseStatusException.class, () -> service.importVersions(
                target,
                user,
                new BookVersionImportRequest(
                        List.of(new SourceSelection(11L, List.of(101L))),
                        SourceHandling.MERGE)));
        verify(aggregationService, never())
                .aggregatePairInCurrentTransaction(any(), any(), any());

        when(versionService.getVersions(target)).thenReturn(List.of());
        service.importVersions(target, user, new BookVersionImportRequest(
                List.of(new SourceSelection(11L, List.of(101L, 102L))),
                SourceHandling.MERGE));
        verify(aggregationService).aggregatePairInCurrentTransaction(10L, 11L, user);
    }

    private Book book(Long id, String title, User user) {
        return Book.builder()
                .id(id)
                .title(title)
                .author("刘慈欣")
                .format("epub")
                .filePath("/books/" + id + ".epub")
                .user(user)
                .build();
    }

    private BookVersion version(Long id, Book book) {
        return BookVersion.builder()
                .id(id)
                .book(book)
                .displayName(id + ".epub")
                .format("epub")
                .filePath("/books/" + id + ".epub")
                .primaryVersion(id.equals(101L))
                .build();
    }
}
