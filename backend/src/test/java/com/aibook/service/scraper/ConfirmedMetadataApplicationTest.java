package com.aibook.service.scraper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.service.AuthorService;
import com.aibook.service.SystemConfigService;
import com.aibook.service.TagService;
import com.aibook.util.BookMetadataSources;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ConfirmedMetadataApplicationTest {
    @Test
    void confirmationOnlyFillsEmptyFieldsAndRecordsSourceWithoutPublishingCache() {
        var repository = mock(BookRepository.class);
        var cache = mock(MetadataCacheService.class);
        var covers = mock(CoverDownloadService.class);
        var authors = mock(AuthorService.class);
        var tags = mock(TagService.class);
        var service = new MetadataScrapingService(repository, List.of(), cache, covers,
                mock(SystemConfigService.class), authors, tags);
        var user = User.builder().id(1L).build();
        var book = Book.builder().id(7L).title("三体").author("原作者").isbn("原ISBN")
                .publisher("原出版社").coverUrl("/原封面.jpg").rating(8).user(user).build();
        when(repository.findByIdAndUserAndDeletedAtIsNull(7L, user)).thenReturn(Optional.of(book));
        var metadata = MetadataScraper.BookMetadata.builder().title("三体：黑暗森林").author("候选作者")
                .isbn("候选ISBN").publisher("候选出版社").coverUrl("https://example.com/new.jpg")
                .rating(9.0).description("候选简介").publishDate("2008").language("zh").build();
        var result = service.applyConfirmedCandidate(7L, user, metadata);
        assertThat(book.getTitle()).isEqualTo("三体");
        assertThat(book.getAuthor()).isEqualTo("原作者");
        assertThat(book.getIsbn()).isEqualTo("原ISBN");
        assertThat(book.getPublisher()).isEqualTo("原出版社");
        assertThat(book.getCoverUrl()).isEqualTo("/原封面.jpg");
        assertThat(book.getRating()).isEqualTo(8);
        assertThat(book.getDescription()).isEqualTo("候选简介");
        assertThat(result.getUpdatedFields()).containsExactlyInAnyOrder("description", "publishDate", "language");
        assertThat(BookMetadataSources.read(book)).containsEntry("description", "豆瓣读书（人工确认）");
        verify(repository).save(book);
        verify(authors).synchronizeBook(book);
        verify(cache, never()).saveToCache(any(), any());
        verifyNoInteractions(covers);
    }

    @Test
    void deletedOrOtherAccountsBookCannotBeUpdated() {
        var repository = mock(BookRepository.class);
        var service = new MetadataScrapingService(repository, List.of(), mock(MetadataCacheService.class),
                mock(CoverDownloadService.class), mock(SystemConfigService.class), mock(AuthorService.class), mock(TagService.class));
        var user = User.builder().id(2L).build();
        when(repository.findByIdAndUserAndDeletedAtIsNull(7L, user)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.applyConfirmedCandidate(7L, user,
                MetadataScraper.BookMetadata.builder().title("候选").build()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        verify(repository, never()).save(any());
    }
}
