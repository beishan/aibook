package com.aibook.service.crawler;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aibook.model.entity.*;
import com.aibook.repository.*;
import com.aibook.service.OperationLogService;
import com.aibook.service.CrawlerSettingsService;
import com.aibook.util.CrawlerAutoBlacklist;
import com.aibook.util.CrawlerAutoBlacklist.Configuration;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;
import org.springframework.web.server.ResponseStatusException;

class CrawlerAutoBlacklistTest {
    @Test
    void literalKeywordsMatchTitleAuthorOrDescriptionAndTagsMatchExactlyIgnoringCase() {
        var config = new Configuration(true, List.of("[.*]", "MYTH"), List.of("历史", "AAA"));
        var book = CrawlerBook.builder().bookName("普通书").tags("aaa1").build();
        assertThat(CrawlerAutoBlacklist.reason(config, book)).isNull();
        book.setAuthor("Myth writer");
        assertThat(CrawlerAutoBlacklist.reason(config, book)).contains("MYTH");
        book.setAuthor(null);
        book.setDescription("a myth story");
        assertThat(CrawlerAutoBlacklist.reason(config, book)).contains("MYTH");
        book.setDescription(null);
        book.setBookName("书名[.*]");
        assertThat(CrawlerAutoBlacklist.reason(config, book)).contains("[.*]");
        book.setBookName(null);
        book.setTags("其他，历史\naaa");
        assertThat(CrawlerAutoBlacklist.reason(config, book)).contains("历史");
        book.setTags("aaa");
        assertThat(CrawlerAutoBlacklist.reason(config, book)).contains("AAA");
        assertThat(CrawlerAutoBlacklist.reason(new Configuration(false, List.of(), List.of("AAA")), book)).isNull();
    }

    @Test
    void configurationNormalizesEmptyValuesAndRoundTripsWithoutChangingLegacyDefaults() {
        var config = CrawlerAutoBlacklist.normalize(new Configuration(true, List.of(" a ", "", "a"), null));
        assertThat(config.keywords()).containsExactly("a");
        assertThat(config.tags()).isEmpty();
        assertThat(CrawlerAutoBlacklist.decode(CrawlerAutoBlacklist.encode(config))).isEqualTo(config);
        assertThat(CrawlerAutoBlacklist.decode(null).enabled()).isFalse();
        assertThatThrownBy(() -> CrawlerAutoBlacklist.normalize(
                new Configuration(true, List.of("x".repeat(201)), null)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> CrawlerAutoBlacklist.decode("{bad"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void taskChecksLatestSiteConfigurationBeforeMakingAnyHttpRequest() {
        try (Fixture f = new Fixture(CrawlerTask.TaskType.BOOK_CONTENT)) {
            f.site.setAutoBlacklistJson(null);
            CrawlerSite latest = CrawlerSite.builder().id(2L).user(f.user)
                    .autoBlacklistJson(CrawlerAutoBlacklist.encode(new Configuration(true, List.of("书"), List.of())))
                    .build();
            when(f.sites.findById(2L)).thenReturn(Optional.of(latest));
            f.service.run(f.task.getId());
            f.assertCancelled("关键字：书");
            verifyNoInteractions(f.http, f.parser);
        }
    }

    @Test
    void newlyParsedTagsCancelBeforeChapterListOrContentRequests() throws Exception {
        try (Fixture f = new Fixture(CrawlerTask.TaskType.BOOK_FULL_CRAWL)) {
            when(f.http.get(f.site, f.book.getBookUrl()))
                    .thenReturn(new CrawlerHttpClient.FetchResult("detail", 200, 1, null, null));
            when(f.parser.parseBookDetail("detail", f.book.getBookUrl(), f.site.getRule()))
                    .thenReturn(metadata());
            f.service.run(f.task.getId());
            f.assertCancelled("标签：禁止");
            verify(f.http, times(1)).get(any(), anyString());
            verify(f.parser, never()).parseChapterList(anyString(), anyString(), any());
        }
    }

    @Test
    void scanFetchesTagsBlacklistsNewBookAndRecordsReasonInsteadOfNewResult() throws Exception {
        try (Fixture f = new Fixture(CrawlerTask.TaskType.SITE_SCAN)) {
            f.task.setCrawlerBook(null);
            f.task.setScanStartUrl("https://example.com/list");
            f.task.setScanMaxPages(1);
            when(f.http.validateSiteUrl(eq(f.site), anyString()))
                    .thenAnswer(inv -> URI.create(inv.getArgument(1)));
            when(f.http.get(eq(f.site), anyString()))
                    .thenReturn(new CrawlerHttpClient.FetchResult("page", 200, 1, null, null));
            when(f.parser.parseBookList("page", "https://example.com/list", f.site.getRule()))
                    .thenReturn(List.of(new BookCrawlerParser.ParsedDiscovery("external", "书", "作者",
                            "", "", "", f.book.getBookUrl())));
            when(f.parser.parseBookDetail("page", f.book.getBookUrl(), f.site.getRule()))
                    .thenReturn(metadata());
            f.service.run(f.task.getId());
            var result = ArgumentCaptor.forClass(CrawlerScanResult.class);
            verify(f.results).save(result.capture());
            assertThat(result.getValue().getResultStatus()).isEqualTo(CrawlerScanResult.ResultStatus.BLACKLISTED);
            assertThat(result.getValue().getErrorMessage()).contains("标签：禁止");
            verify(f.management).markAutoBlacklisted(any(CrawlerBook.class));
            assertThat(f.task.getNewBookCount()).isZero();
            assertThat(f.task.getSuccessCount()).isZero();
        }
    }

    private static BookCrawlerParser.ParsedBook metadata() {
        return new BookCrawlerParser.ParsedBook("external", "书", "作者", "", "简介", "分类",
                List.of("禁止"), "连载", "", "https://example.com/chapters");
    }

    private static class Fixture implements AutoCloseable {
        final User user = User.builder().id(1L).build();
        final CrawlerSite site = CrawlerSite.builder().id(2L).user(user).siteName("测试站")
                .baseUrl("https://example.com").enabled(true)
                .autoBlacklistJson(CrawlerAutoBlacklist.encode(new Configuration(true, List.of(), List.of("禁止"))))
                .build();
        final CrawlerBook book = CrawlerBook.builder().id(3L).site(site).bookName("书")
                .bookUrl("https://example.com/book/1").build();
        final CrawlerTask task;
        final CrawlerSiteRepository sites = mock(CrawlerSiteRepository.class);
        final CrawlerTaskRepository tasks = mock(CrawlerTaskRepository.class);
        final CrawlerBookRepository books = mock(CrawlerBookRepository.class);
        final CrawlerScanResultRepository results = mock(CrawlerScanResultRepository.class);
        final CrawlerManagementService management = mock(CrawlerManagementService.class);
        final CrawlerHttpClient http = mock(CrawlerHttpClient.class);
        final BookCrawlerParser parser = mock(BookCrawlerParser.class);
        final CrawlerTaskService service;

        Fixture(CrawlerTask.TaskType type) {
            site.attachRule(new CrawlerSiteRule());
            task = CrawlerTask.builder().site(site).user(user).crawlerBook(book).type(type).build();
            when(tasks.findById(task.getId())).thenAnswer(inv -> Optional.of(
                    task.getStatus() == CrawlerTask.TaskStatus.CANCELLED
                            || task.getStatus() == CrawlerTask.TaskStatus.PARTIAL_SUCCESS
                            ? CrawlerTask.builder().id(task.getId()).site(site).user(user).crawlerBook(book)
                                    .type(type).status(CrawlerTask.TaskStatus.RUNNING).build() : task));
            when(tasks.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(books.save(any())).thenAnswer(inv -> {
                CrawlerBook saved = inv.getArgument(0);
                if (saved.getId() == null) saved.setId(4L);
                return saved;
            });
            when(parser.supports(site)).thenReturn(true);
            service = new CrawlerTaskService(sites, mock(CrawlerDiscoveryPageRepository.class), books,
                    mock(CrawlerChapterRepository.class), tasks, results, mock(CrawlerTaskLogRepository.class),
                    management, mock(CrawlerChapterAttemptMetricService.class), mock(OperationLogService.class),
                    mock(CrawlerExportService.class), http, List.of(parser), mock(ApplicationContext.class),
                    mock(CrawlerSettingsService.class));
        }

        void assertCancelled(String reason) {
            var saved = ArgumentCaptor.forClass(CrawlerTask.class);
            verify(tasks, atLeastOnce()).save(saved.capture());
            CrawlerTask finalTask = saved.getAllValues().getLast();
            assertThat(finalTask.getStatus()).isEqualTo(CrawlerTask.TaskStatus.CANCELLED);
            assertThat(finalTask.getErrorMessage()).contains(reason);
            assertThat(finalTask.getFinishedAt()).isNotNull();
            verify(management).markAutoBlacklisted(book);
            assertThat(book.getCrawlStatus()).isEqualTo(CrawlerBook.CrawlStatus.PAUSED);
        }

        @Override
        public void close() {
            service.shutdown();
        }
    }
}
