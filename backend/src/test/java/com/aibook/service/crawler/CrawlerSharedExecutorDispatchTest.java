package com.aibook.service.crawler;

import static org.mockito.Mockito.*;

import com.aibook.model.entity.*;
import com.aibook.repository.*;
import com.aibook.service.CrawlerSettingsService;
import com.aibook.service.OperationLogService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

class CrawlerSharedExecutorDispatchTest {
    @Test
    @SuppressWarnings("unchecked")
    void executorBusyInAnotherQueueCannotBeAllocatedAgain() {
        var sites = mock(CrawlerSiteRepository.class);
        var tasks = mock(CrawlerTaskRepository.class);
        var queues = mock(CrawlerTaskQueueRepository.class);
        var execution = mock(CrawlerQueueExecutorService.class);
        var settings = mock(CrawlerSettingsService.class);
        when(settings.maxConcurrentTasks()).thenReturn(4);
        var user = User.builder().id(1L).build();
        var site = CrawlerSite.builder().id(2L).siteName("网站二").user(user).build();
        var queue = CrawlerTaskQueue.builder().id(9L).user(user).site(site).maxConcurrentTasks(1).build();
        var shared = CrawlerQueueExecutor.builder().id(3L).queue(queue).name("共享执行器").build();
        var task = CrawlerTask.builder().id("waiting").site(site).queue(queue)
                .type(CrawlerTask.TaskType.BOOK_CONTENT).queueOrder(1L)
                .createdAt(java.time.LocalDateTime.now()).build();
        when(queues.findAll()).thenReturn(List.of(queue));
        when(tasks.findByStatusOrderByQueueOrderAsc(CrawlerTask.TaskStatus.WAITING)).thenReturn(List.of(task));
        when(execution.enabledExecutors(9L)).thenReturn(List.of(shared));
        var service = new CrawlerTaskService(sites, mock(CrawlerDiscoveryPageRepository.class),
                mock(CrawlerBookRepository.class), mock(CrawlerChapterRepository.class), tasks,
                mock(CrawlerScanResultRepository.class), mock(CrawlerTaskLogRepository.class),
                mock(CrawlerManagementService.class), mock(CrawlerChapterAttemptMetricService.class),
                mock(OperationLogService.class), mock(CrawlerExportService.class), mock(CrawlerHttpClient.class),
                List.of(), mock(ApplicationContext.class), settings);
        ReflectionTestUtils.setField(service, "taskQueueRepository", queues);
        ReflectionTestUtils.setField(service, "queueExecutorService", execution);
        Map<String, Long> active = (Map<String, Long>) ReflectionTestUtils.getField(service, "activeTaskExecutors");
        active.put("running-in-another-queue", 3L);
        Map<String, Long> activeQueues = (Map<String, Long>) ReflectionTestUtils.getField(service, "activeTaskQueues");
        activeQueues.put("running-in-another-queue", 2L);
        var view = new com.aibook.dto.crawler.CrawlerDtos.QueueExecutorView(
                3L, 9L, "共享执行器", null, false, true, true, false, "DEFAULT", "ORDERED",
                null, List.of(), 1, null, List.of(), true, false, null);
        when(execution.list(user, 9L)).thenReturn(List.of(view));
        var runtime = service.queueExecutors(user, 9L).getFirst();
        org.assertj.core.api.Assertions.assertThat(runtime.busy()).isTrue();
        org.assertj.core.api.Assertions.assertThat(runtime.busyQueueId()).isEqualTo(2L);
        when(execution.share(user, 9L, 3L)).thenReturn(view);
        var added = service.shareQueueExecutor(user, 9L, 3L);
        org.assertj.core.api.Assertions.assertThat(added.busy()).isTrue();
        org.assertj.core.api.Assertions.assertThat(added.busyQueueId()).isEqualTo(2L);
        verify(execution).share(user, 9L, 3L);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.updateQueueExecutor(user, 9L, 3L, null))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(execution, never()).update(user, 9L, 3L, null);
        var secondQueue = CrawlerTaskQueue.builder().id(10L).maxConcurrentTasks(1).build();
        when(execution.enabledExecutors(10L)).thenReturn(List.of(shared));
        Integer capacity = ReflectionTestUtils.invokeMethod(service, "distinctExecutorCapacity", List.of(queue, secondQueue));
        org.assertj.core.api.Assertions.assertThat(capacity).isEqualTo(1);
        try {
            service.dispatchWaitingTasks();
            verify(tasks, never()).save(task);
            verify(execution, never()).canExecute(site, shared, 9L);
        } finally {
            ((java.util.concurrent.ExecutorService) ReflectionTestUtils.getField(service, "executor")).shutdownNow();
        }
    }
}
