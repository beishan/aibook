package com.aibook.service.scraper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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

class ScrapingModesApplicationTest {
    @Test
    void explicitAutomaticScrapeUsesEnabledPriorityWithoutStaleCacheOrOverwritingExistingFields() {
        var books = mock(BookRepository.class);
        var cache = mock(MetadataCacheService.class);
        var config = mock(SystemConfigService.class);
        var low = mock(MetadataScraper.class);
        var high = mock(MetadataScraper.class);
        var disabled = mock(MetadataScraper.class);
        for (var scraper : List.of(low, high, disabled)) {
            when(scraper.supports(any())).thenReturn(true);
            when(scraper.getName()).thenReturn("来源" + List.of(low, high, disabled).indexOf(scraper));
        }
        when(low.getConfigKey()).thenReturn("low");
        when(high.getConfigKey()).thenReturn("high");
        when(disabled.getConfigKey()).thenReturn("disabled");
        when(config.getBooleanConfig(anyString(), anyBoolean())).thenAnswer(call ->
                !call.getArgument(0).toString().contains("disabled"));
        when(config.getIntConfig(anyString(), anyInt())).thenAnswer(call ->
                call.getArgument(0).toString().contains("high") ? 1 : 2);
        when(high.scrape(any())).thenReturn(MetadataScraper.BookMetadata.builder()
                .title("更正书名").author("优先作者").description("优先简介").build());
        when(low.scrape(any())).thenReturn(MetadataScraper.BookMetadata.builder()
                .author("低优先作者").publisher("补充出版社").build());
        var service = new MetadataScrapingService(books, List.of(low, disabled, high), cache,
                mock(CoverDownloadService.class), config, mock(AuthorService.class), mock(TagService.class));
        var book = Book.builder().id(7L).title("原书名").build();
        when(books.findById(7L)).thenReturn(Optional.of(book));
        var result = service.scrapeBookWithResult(book, false, true);
        assertThat(result.isSuccess()).isTrue();
        assertThat(book.getTitle()).isEqualTo("原书名");
        assertThat(book.getAuthor()).isEqualTo("优先作者");
        assertThat(book.getPublisher()).isEqualTo("补充出版社");
        var order = inOrder(high, low);
        order.verify(high).scrape(any());
        order.verify(low).scrape(any());
        verify(disabled, never()).scrape(any());
        verify(cache, never()).getFromCache(any());
        verify(books).save(book);
    }

    @Test
    void manualSaveUsesChosenSourceAndDownloadsOnlyNewCover() {
        var books = mock(BookRepository.class);
        var covers = mock(CoverDownloadService.class);
        var service = new MetadataScrapingService(books, List.of(), mock(MetadataCacheService.class), covers,
                mock(SystemConfigService.class), mock(AuthorService.class), mock(TagService.class));
        var user = User.builder().id(1L).build();
        var book = Book.builder().id(7L).title("原书名").user(user).build();
        when(books.findByIdAndUserAndDeletedAtIsNull(7L, user)).thenReturn(Optional.of(book));
        when(covers.downloadCover(book)).thenAnswer(call -> {
            book.setCoverUrl("covers/downloaded.jpg");
            return book;
        });
        var metadata = MetadataScraper.BookMetadata.builder().title("候选书名")
                .description("人工核对的简介").coverUrl("https://example.com/cover.jpg").build();
        var result = service.applyConfirmedMetadata(7L, user, metadata, "Open Library");
        assertThat(book.getTitle()).isEqualTo("原书名");
        assertThat(book.getCoverUrl()).isEqualTo("covers/downloaded.jpg");
        assertThat(BookMetadataSources.read(book)).containsEntry("description", "Open Library（人工确认）");
        assertThat(result.getSources()).containsExactly("Open Library（人工确认）");
        verify(covers).downloadCover(book);
    }
}
