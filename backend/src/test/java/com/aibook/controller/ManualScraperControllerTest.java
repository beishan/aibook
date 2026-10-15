package com.aibook.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.service.BookService;
import com.aibook.service.SystemConfigService;
import com.aibook.service.scraper.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ManualScraperControllerTest {
    @Test
    void manualStartChecksCurrentOwnersNonDeletedBookBeforeCreatingTask() {
        var books = mock(BookRepository.class);
        var trials = mock(ScraperTrialService.class);
        var controller = new ScraperController(mock(MetadataScrapingService.class),
                mock(CoverDownloadService.class), mock(MetadataCacheService.class), books,
                mock(BookService.class), mock(SystemConfigService.class), List.of(),
                mock(ScraperProxyService.class), trials);
        var user = User.builder().id(1L).build();
        when(books.findByIdAndUserAndDeletedAtIsNull(7L, user)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> controller.startManual("douban", 7L, user))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        verifyNoInteractions(trials);
        var book = Book.builder().id(7L).title("目标书籍").user(user).build();
        when(books.findByIdAndUserAndDeletedAtIsNull(7L, user)).thenReturn(Optional.of(book));
        when(trials.startManual("douban", book, 1L)).thenReturn("owned-task");
        assertThat(controller.startManual("douban", 7L, user)).containsEntry("taskId", "owned-task");
        verify(trials).startManual("douban", book, 1L);
    }
    @Test
    void confirmationForwardsOnlySelectedFieldsAndSupportsOlderRequestsWithoutABody() {
        var trials = mock(ScraperTrialService.class);
        var scraping = mock(MetadataScrapingService.class);
        var controller = new ScraperController(scraping, mock(CoverDownloadService.class),
                mock(MetadataCacheService.class), mock(BookRepository.class), mock(BookService.class),
                mock(SystemConfigService.class), List.of(), mock(ScraperProxyService.class), trials);
        var user = User.builder().id(1L).build();
        var metadata = MetadataScraper.BookMetadata.builder().description("新简介").build();
        var result = MetadataScrapingService.ScrapeResult.builder().success(true)
                .updatedFields(List.of("description")).message("已保存").build();
        when(scraping.applyConfirmedMetadata(eq(7L), eq(user), same(metadata), eq("京东读书"),
                anySet(), eq("SERIES"), isNull()))
                .thenReturn(result);
        when(trials.confirmManual(eq("owned-task"), eq(1L), any())).thenAnswer(call -> {
            ScraperTrialService.ManualApply apply = call.getArgument(2);
            return apply.apply(7L, metadata, "京东读书");
        });
        controller.confirmManual("owned-task", user,
                new ScraperController.ConfirmationRequest(java.util.Set.of("description")));
        verify(scraping).applyConfirmedMetadata(7L, user, metadata, "京东读书",
                java.util.Set.of("description"), "SERIES", null);
        controller.confirmManual("owned-task", user, null);
        verify(scraping).applyConfirmedMetadata(7L, user, metadata, "京东读书",
                java.util.Set.of(), "SERIES", null);
    }

}
