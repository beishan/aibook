package com.aibook.service.crawler;

import com.aibook.model.entity.CrawlerSite;
import com.aibook.model.entity.CrawlerBook;
import com.aibook.model.entity.CrawlerChapter;
import com.aibook.model.entity.CrawlerTask;
import com.aibook.model.entity.User;
import com.aibook.repository.CrawlerBookRepository;
import com.aibook.repository.CrawlerChapterRepository;
import com.aibook.repository.CrawlerSiteRepository;
import com.aibook.repository.CrawlerTaskRepository;
import com.aibook.repository.CrawlerTaskLogRepository;
import com.aibook.service.OperationLogService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CrawlerTaskManagementTest {

    @Test
    void stopsBookTaskAfterConfiguredConsecutiveRequestFailures() throws Exception {
        User user = user();
        CrawlerSite site = CrawlerSite.builder().id(2L).user(user).siteName("示例站").build();
        site.attachRule(new com.aibook.model.entity.CrawlerSiteRule());
        CrawlerBook book = CrawlerBook.builder().id(8L).site(site).bookName("代理失败书籍").build();
        List<CrawlerChapter> chaptersToCrawl = java.util.stream.IntStream.rangeClosed(1, 8)
                .mapToObj(index -> CrawlerChapter.builder().id((long) index).crawlerBook(book)
                        .chapterIndex(index).chapterName("第" + index + "章")
                        .chapterUrl("https://example.com/chapter/" + index).build())
                .toList();
        CrawlerTask task = CrawlerTask.builder().user(user).site(site).crawlerBook(book)
                .type(CrawlerTask.TaskType.BOOK_CONTENT).build();
        CrawlerTaskRepository tasks = mock(CrawlerTaskRepository.class);
        CrawlerBookRepository books = mock(CrawlerBookRepository.class);
        CrawlerChapterRepository chapters = mock(CrawlerChapterRepository.class);
        CrawlerHttpClient httpClient = mock(CrawlerHttpClient.class);
        BookCrawlerParser parser = mock(BookCrawlerParser.class);
        when(tasks.findById(task.getId())).thenReturn(Optional.of(task));
        when(tasks.save(any(CrawlerTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(books.save(any(CrawlerBook.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chapters.findByCrawlerBookOrderByChapterIndexAsc(book)).thenReturn(chaptersToCrawl);
        when(chapters.countByCrawlerBook(book)).thenReturn(8L);
        when(parser.supports(site)).thenReturn(true);
        when(httpClient.maxConsecutiveFailures()).thenReturn(5);
        when(httpClient.get(eq(site), anyString())).thenThrow(new IOException("代理连接失败"));
        CrawlerTaskService service = new CrawlerTaskService(mock(CrawlerSiteRepository.class), books,
                chapters, tasks, mock(CrawlerTaskLogRepository.class), mock(CrawlerManagementService.class),
                mock(OperationLogService.class), httpClient, List.of(parser), mock(ApplicationContext.class));
        try {
            service.run(task.getId());

            assertThat(task.getStatus()).isEqualTo(CrawlerTask.TaskStatus.FAILED);
            assertThat(task.getErrorMessage()).contains("连续请求失败 5 次", "代理连接失败");
            verify(httpClient, times(5)).get(eq(site), anyString());
            assertThat(chaptersToCrawl.subList(0, 5))
                    .allMatch(chapter -> chapter.getCrawlStatus() == CrawlerChapter.CrawlStatus.FAILED);
            assertThat(chaptersToCrawl.subList(5, 8))
                    .allMatch(chapter -> chapter.getCrawlStatus() != CrawlerChapter.CrawlStatus.FAILED);
        } finally {
            service.shutdown();
        }
    }

    @Test
    void updatesPausedTaskPriority() {
        User user = user();
        CrawlerTask task = task(user, CrawlerTask.TaskStatus.PAUSED);
        CrawlerTaskRepository tasks = mock(CrawlerTaskRepository.class);
        CrawlerManagementService management = mock(CrawlerManagementService.class);
        when(management.ownedTask(user, task.getId())).thenReturn(task);
        when(management.taskView(task)).thenCallRealMethod();
        when(tasks.save(any(CrawlerTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CrawlerTaskService service = service(tasks, management);
        try {
            var result = service.updateTask(user, task.getId(), CrawlerTask.Priority.HIGH);

            assertThat(result.priority()).isEqualTo("HIGH");
            verify(tasks).save(task);
        } finally {
            service.shutdown();
        }
    }

    @Test
    void updatesFailedTaskPriority() {
        User user = user();
        CrawlerTask task = task(user, CrawlerTask.TaskStatus.FAILED);
        CrawlerTaskRepository tasks = mock(CrawlerTaskRepository.class);
        CrawlerManagementService management = mock(CrawlerManagementService.class);
        when(management.ownedTask(user, task.getId())).thenReturn(task);
        when(management.taskView(task)).thenCallRealMethod();
        when(tasks.save(any(CrawlerTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CrawlerTaskService service = service(tasks, management);
        try {
            var result = service.updateTask(user, task.getId(), CrawlerTask.Priority.HIGH);

            assertThat(result.priority()).isEqualTo("HIGH");
            verify(tasks).save(task);
        } finally {
            service.shutdown();
        }
    }

    @Test
    void deletesFinishedAndQueuedTaskButRejectsRunningTask() {
        User user = user();
        CrawlerTask finished = task(user, CrawlerTask.TaskStatus.SUCCESS);
        CrawlerTask queued = task(user, CrawlerTask.TaskStatus.WAITING);
        CrawlerTask running = task(user, CrawlerTask.TaskStatus.RUNNING);
        CrawlerTaskRepository tasks = mock(CrawlerTaskRepository.class);
        CrawlerManagementService management = mock(CrawlerManagementService.class);
        when(management.ownedTask(user, finished.getId())).thenReturn(finished);
        when(management.ownedTask(user, queued.getId())).thenReturn(queued);
        when(management.ownedTask(user, running.getId())).thenReturn(running);
        CrawlerTaskService service = service(tasks, management);
        try {
            service.deleteTask(user, finished.getId());
            verify(tasks).delete(finished);

            service.deleteTask(user, queued.getId());
            verify(tasks).delete(queued);

            assertThatThrownBy(() -> service.deleteTask(user, running.getId()))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("先暂停或取消");
            verify(tasks, never()).delete(running);
        } finally {
            service.shutdown();
        }
    }

    @Test
    void deletingFinishedUpdateCheckTaskOnlyRemovesTheRequestedRecord() {
        User user = user();
        CrawlerSite site = CrawlerSite.builder().id(2L).user(user).siteName("示例站").build();
        CrawlerBook book = CrawlerBook.builder().id(8L).site(site).bookName("已采集书籍")
                .crawlStatus(CrawlerBook.CrawlStatus.COMPLETED).autoUpdateEnabled(true).build();
        CrawlerTask update = CrawlerTask.builder().user(user).site(site).crawlerBook(book)
                .type(CrawlerTask.TaskType.BOOK_UPDATE_CHECK).status(CrawlerTask.TaskStatus.SUCCESS).build();
        CrawlerTaskRepository tasks = mock(CrawlerTaskRepository.class);
        CrawlerBookRepository books = mock(CrawlerBookRepository.class);
        CrawlerManagementService management = mock(CrawlerManagementService.class);
        when(management.ownedTask(user, update.getId())).thenReturn(update);
        CrawlerTaskService service = service(tasks, books, management);
        try {
            service.deleteTask(user, update.getId());

            verify(tasks).delete(update);
            verify(books, never()).save(any());
        } finally {
            service.shutdown();
        }
    }

    @Test
    void doesNotStartTaskThatWasPausedAfterItEnteredTheQueue() {
        User user = user();
        CrawlerTask paused = task(user, CrawlerTask.TaskStatus.PAUSED);
        CrawlerTaskRepository tasks = mock(CrawlerTaskRepository.class);
        CrawlerManagementService management = mock(CrawlerManagementService.class);
        when(tasks.findById(paused.getId())).thenReturn(java.util.Optional.of(paused));
        CrawlerTaskService service = service(tasks, management);
        try {
            service.run(paused.getId());

            verify(tasks, never()).save(any());
        } finally {
            service.shutdown();
        }
    }

    @Test
    void pausesInterruptedTasksOnStartupInsteadOfSubmittingThemAgain() {
        User user = user();
        CrawlerBook book = CrawlerBook.builder().id(8L).bookName("未完成的书").build();
        CrawlerTask interrupted = CrawlerTask.builder().user(user)
                .site(CrawlerSite.builder().id(2L).user(user).siteName("示例站").build())
                .crawlerBook(book).type(CrawlerTask.TaskType.BOOK_FULL_CRAWL)
                .status(CrawlerTask.TaskStatus.RUNNING).build();
        CrawlerTaskRepository tasks = mock(CrawlerTaskRepository.class);
        CrawlerBookRepository books = mock(CrawlerBookRepository.class);
        CrawlerManagementService management = mock(CrawlerManagementService.class);
        when(tasks.findByStatusIn(any())).thenReturn(List.of(interrupted));
        CrawlerTaskService service = service(tasks, books, management);
        try {
            service.onApplicationEvent(null);

            assertThat(interrupted.getStatus()).isEqualTo(CrawlerTask.TaskStatus.PAUSED);
            assertThat(interrupted.getErrorMessage()).contains("服务重启后已自动暂停");
            assertThat(book.getCrawlStatus()).isEqualTo(CrawlerBook.CrawlStatus.PAUSED);
            verify(tasks).save(interrupted);
            verify(books).save(book);
            verify(tasks, never()).findById(interrupted.getId());
        } finally {
            service.shutdown();
        }
    }

    private CrawlerTaskService service(CrawlerTaskRepository tasks, CrawlerManagementService management) {
        return service(tasks, mock(CrawlerBookRepository.class), management);
    }

    private CrawlerTaskService service(CrawlerTaskRepository tasks, CrawlerBookRepository books,
            CrawlerManagementService management) {
        return new CrawlerTaskService(mock(CrawlerSiteRepository.class), books,
                mock(CrawlerChapterRepository.class), tasks, mock(CrawlerTaskLogRepository.class),
                management, mock(OperationLogService.class),
                mock(CrawlerHttpClient.class), List.of(),
                mock(ApplicationContext.class));
    }

    private CrawlerTask task(User user, CrawlerTask.TaskStatus status) {
        CrawlerSite site = CrawlerSite.builder().id(2L).user(user).siteName("示例站").build();
        return CrawlerTask.builder().user(user).site(site).type(CrawlerTask.TaskType.SITE_SCAN)
                .status(status).priority(CrawlerTask.Priority.LOW).build();
    }

    private User user() {
        return User.builder().id(1L).username("owner").build();
    }
}
