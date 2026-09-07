package com.aibook.service.crawler;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.CrawlerBook;
import com.aibook.model.entity.CrawlerChapter;
import com.aibook.model.entity.CrawlerSite;
import com.aibook.model.entity.OperationLog;
import com.aibook.model.entity.User;
import com.aibook.repository.CrawlerBookRepository;
import com.aibook.repository.CrawlerChapterRepository;
import com.aibook.repository.CrawlerSiteRepository;
import com.aibook.repository.CrawlerTaskRepository;
import com.aibook.repository.CrawlerTaskLogRepository;
import com.aibook.service.OperationLogService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CrawlerTaskServiceStatusTest {

    @Test
    void manuallyMarksPartialBookAsCompletedAndReadyToImport() {
        User user = User.builder().id(1L).username("owner").build();
        CrawlerSite site = CrawlerSite.builder().id(2L).user(user).siteName("示例站").build();
        CrawlerBook book = CrawlerBook.builder().id(3L).site(site).bookName("示例书")
                .crawlStatus(CrawlerBook.CrawlStatus.PARTIAL_SUCCESS).build();
        CrawlerChapter chapter = CrawlerChapter.builder().crawlerBook(book).content("已有正文").build();
        CrawlerBookRepository books = mock(CrawlerBookRepository.class);
        CrawlerChapterRepository chapters = mock(CrawlerChapterRepository.class);
        CrawlerTaskRepository tasks = mock(CrawlerTaskRepository.class);
        CrawlerManagementService management = mock(CrawlerManagementService.class);
        OperationLogService operationLogs = mock(OperationLogService.class);
        when(management.ownedBook(user, 3L)).thenReturn(book);
        when(chapters.findByCrawlerBookOrderByChapterIndexAsc(book)).thenReturn(List.of(chapter));
        when(books.save(any(CrawlerBook.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CrawlerTaskService service = new CrawlerTaskService(mock(CrawlerSiteRepository.class), books,
                chapters, tasks, mock(CrawlerTaskLogRepository.class), management, operationLogs,
                mock(CrawlerExportService.class),
                mock(CrawlerHttpClient.class),
                List.of(), mock(ApplicationContext.class));
        try {
            when(management.bookView(book)).thenCallRealMethod();
            var result = service.setBookStatus(user, 3L, CrawlerBook.CrawlStatus.COMPLETED, true);

            assertThat(result.crawlStatus()).isEqualTo("COMPLETED");
            assertThat(book.getImportStatus()).isEqualTo(CrawlerBook.ImportStatus.READY);
            assertThat(book.getLastCrawlTime()).isNotNull();
            assertThat(book.getAutoUpdateEnabled()).isFalse();
            verify(tasks, never()).save(any());
            verify(operationLogs).recordEntry(eq(user), eq(OperationLog.Action.CRAWLER_TASK),
                    isNull(), eq("示例书"), contains("人工修改采集状态"), contains("新状态：COMPLETED"));
        } finally {
            service.shutdown();
        }
    }

    @Test
    void changesAutomaticLibrarySyncForImportedBook() {
        User user = User.builder().id(1L).username("owner").build();
        CrawlerSite site = CrawlerSite.builder().id(2L).user(user).siteName("示例站").build();
        CrawlerBook book = CrawlerBook.builder().id(3L).site(site).bookName("示例书")
                .libraryBook(Book.builder().id(9L).user(user).title("示例书").format("epub")
                        .filePath("book.epub").build())
                .autoSyncLibrary(true).build();
        CrawlerBookRepository books = mock(CrawlerBookRepository.class);
        CrawlerManagementService management = mock(CrawlerManagementService.class);
        when(management.ownedBook(user, 3L)).thenReturn(book);
        when(management.bookView(book)).thenCallRealMethod();
        when(books.save(any(CrawlerBook.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CrawlerTaskService service = new CrawlerTaskService(mock(CrawlerSiteRepository.class), books,
                mock(CrawlerChapterRepository.class), mock(CrawlerTaskRepository.class),
                mock(CrawlerTaskLogRepository.class), management, mock(OperationLogService.class),
                mock(CrawlerExportService.class), mock(CrawlerHttpClient.class), List.of(),
                mock(ApplicationContext.class));
        try {
            var result = service.setLibrarySync(user, 3L, false);

            assertThat(result.autoSyncLibrary()).isFalse();
            assertThat(book.getAutoSyncLibrary()).isFalse();
            verify(books).save(book);
        } finally {
            service.shutdown();
        }
    }

}
