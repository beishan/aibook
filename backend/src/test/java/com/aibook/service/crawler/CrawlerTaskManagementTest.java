package com.aibook.service.crawler;

import com.aibook.model.entity.CrawlerSite;
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
    void deletesFinishedTaskButRejectsActiveTask() {
        User user = user();
        CrawlerTask finished = task(user, CrawlerTask.TaskStatus.SUCCESS);
        CrawlerTask active = task(user, CrawlerTask.TaskStatus.WAITING);
        CrawlerTaskRepository tasks = mock(CrawlerTaskRepository.class);
        CrawlerManagementService management = mock(CrawlerManagementService.class);
        when(management.ownedTask(user, finished.getId())).thenReturn(finished);
        when(management.ownedTask(user, active.getId())).thenReturn(active);
        CrawlerTaskService service = service(tasks, management);
        try {
            service.deleteTask(user, finished.getId());
            verify(tasks).delete(finished);

            assertThatThrownBy(() -> service.deleteTask(user, active.getId()))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("先取消");
            verify(tasks, never()).delete(active);
        } finally {
            service.shutdown();
        }
    }

    private CrawlerTaskService service(CrawlerTaskRepository tasks, CrawlerManagementService management) {
        return new CrawlerTaskService(mock(CrawlerSiteRepository.class), mock(CrawlerBookRepository.class),
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
