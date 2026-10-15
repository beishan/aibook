package com.aibook.service.scraper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.Tag;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.service.AuthorService;
import com.aibook.service.SystemConfigService;
import com.aibook.service.TagService;
import com.aibook.util.BookMetadataSources;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

class SelectedMetadataOverwriteTest {
    private final BookRepository books = mock(BookRepository.class);
    private final MetadataCacheService cache = mock(MetadataCacheService.class);
    private final CoverDownloadService covers = mock(CoverDownloadService.class);
    private final TagService tags = mock(TagService.class);
    private final AuthorService authors = mock(AuthorService.class);
    private final User user = User.builder().id(1L).build();
    private final Tag oldTag = Tag.builder().id(1L).name("原标签").build();
    private final Tag newTag = Tag.builder().id(2L).name("新标签").build();
    private final Book book = Book.builder().id(7L).title("原书名").author("原作者").isbn("原ISBN")
            .publisher("原出版社").publishDate("2001").description("原简介").coverUrl("covers/old.jpg")
            .coverHidden(true).language("zh").rating(8).user(user)
            .tags(new LinkedHashSet<>(Set.of(oldTag))).build();
    private final MetadataScraper.BookMetadata metadata = MetadataScraper.BookMetadata.builder()
            .title("新书名").author("新作者").isbn("新ISBN").publisher("新出版社").publishDate("2024")
            .description("刮削简介").coverUrl("https://example.com/new.jpg").language("en")
            .rating(7.0).tags(new String[]{"新标签"}).build();
    private final MetadataScrapingService service = new MetadataScrapingService(books, List.of(), cache,
            covers, mock(SystemConfigService.class), authors, tags);

    private void prepare() {
        when(books.findByIdAndUserAndDeletedAtIsNull(7L, user)).thenReturn(Optional.of(book));
        when(cache.titleKey(any())).thenAnswer(call -> "title:" + call.getArgument(0));
        when(cache.isbnKey(any())).thenAnswer(call -> "isbn:" + call.getArgument(0));
        when(tags.getOrCreateTags(List.of("新标签"), user)).thenReturn(Set.of(newTag));
    }

    @Test
    void onlyCheckedDescriptionOverwritesAndOtherExistingFieldsArePreserved() {
        prepare();
        var result = service.applyConfirmedCandidate(7L, user, metadata, Set.of("description"));
        assertThat(book.getDescription()).isEqualTo("刮削简介");
        assertThat(book.getTitle()).isEqualTo("原书名");
        assertThat(book.getAuthor()).isEqualTo("原作者");
        assertThat(book.getIsbn()).isEqualTo("原ISBN");
        assertThat(book.getCoverUrl()).isEqualTo("covers/old.jpg");
        assertThat(book.getRating()).isEqualTo(8);
        assertThat(book.getTags()).containsExactly(oldTag);
        assertThat(book.getCoverHidden()).isTrue();
        assertThat(result.getUpdatedFields()).containsExactly("description");
        assertThat(BookMetadataSources.read(book)).containsEntry("description", "豆瓣读书（人工确认）");
        verifyNoInteractions(covers, tags);
    }

    @Test
    void allSupportedCheckedFieldsReplaceValuesIncludingTagsAndDownloadNewCover() {
        prepare();
        when(covers.downloadCover(book)).thenAnswer(call -> {
            book.setCoverUrl("covers/new.jpg");
            return book;
        });
        var selected = Set.of("title", "author", "isbn", "publisher", "publishDate",
                "description", "coverUrl", "language", "rating", "tags");
        var result = service.applyConfirmedMetadata(7L, user, metadata, "京东读书", selected);
        assertThat(result.getUpdatedFields()).containsExactlyInAnyOrderElementsOf(selected);
        assertThat(book.getTitle()).isEqualTo("新书名");
        assertThat(book.getAuthor()).isEqualTo("新作者");
        assertThat(book.getIsbn()).isEqualTo("新ISBN");
        assertThat(book.getPublisher()).isEqualTo("新出版社");
        assertThat(book.getPublishDate()).isEqualTo("2024");
        assertThat(book.getDescription()).isEqualTo("刮削简介");
        assertThat(book.getCoverUrl()).isEqualTo("covers/new.jpg");
        assertThat(book.getLanguage()).isEqualTo("en");
        assertThat(book.getRating()).isEqualTo(7);
        assertThat(book.getTags()).containsExactly(newTag);
        assertThat(book.getCoverHidden()).isTrue();
        verify(covers).downloadCover(book);
        verify(authors).synchronizeBook(book);
        for (String key : List.of("title:原书名", "title:新书名", "isbn:原ISBN", "isbn:新ISBN")) {
            verify(cache).evictCache(key);
        }
    }

    @Test
    void noSelectionOnlyFillsMissingValuesAndDoesNotReplaceOrMergeExistingTags() {
        prepare();
        book.setPublisher(null);
        var result = service.applyConfirmedCandidate(7L, user, metadata);
        assertThat(book.getPublisher()).isEqualTo("新出版社");
        assertThat(book.getDescription()).isEqualTo("原简介");
        assertThat(book.getTags()).containsExactly(oldTag);
        assertThat(result.getUpdatedFields()).containsExactly("publisher");
        verifyNoInteractions(tags, covers);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"coverHidden", "user", "metadataSources", "filePath", "unknown"})
    void rejectsFieldsOutsideTheMetadataWhitelist(String field) {
        prepare();
        Set<String> requested = new HashSet<>();
        requested.add(field);
        assertThatThrownBy(() -> service.applyConfirmedMetadata(7L, user, metadata, "来源", requested))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        verify(books, never()).save(any());
        verifyNoInteractions(cache);
    }

    @Test
    void rejectsAnEmptySourceFieldInsteadOfErasingExistingContent() {
        prepare();
        metadata.setDescription("   ");
        assertThatThrownBy(() -> service.applyConfirmedMetadata(7L, user, metadata, "来源", Set.of("description")))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        assertThat(book.getDescription()).isEqualTo("原简介");
        verify(books, never()).save(any());
    }
}
