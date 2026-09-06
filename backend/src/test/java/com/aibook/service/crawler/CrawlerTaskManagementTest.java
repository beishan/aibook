package com.aibook.service.crawler;

import com.aibook.model.entity.CrawlerSite;
import com.aibook.model.entity.CrawlerBook;
import com.aibook.model.entity.CrawlerTask;
import com.aibook.model.entity.User;
import com.aibook.repository.CrawlerBookRepository;
import com.aibook.repository.CrawlerChapterRepository;
import com.aibook.repository.CrawlerSiteRepository;
import com.aibook.repository.CrawlerTaskRepository;
import com.aibook.service.OperationLogService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CrawlerTaskManagementTest {

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
    void deletingUpdateCheckTaskStopsAutomaticUpdatesForBook() {
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

            assertThat(book.getAutoUpdateEnabled()).isFalse();
            verify(books).save(book);
            verify(tasks).delete(update);
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
                mock(CrawlerChapterRepository.class), tasks, management, mock(OperationLogService.class),
                mock(CrawlerExportService.class), mock(CrawlerHttpClient.class), List.of(),
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
