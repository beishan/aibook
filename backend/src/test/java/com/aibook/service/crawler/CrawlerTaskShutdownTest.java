package com.aibook.service.crawler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.aibook.repository.*;
import com.aibook.service.CrawlerSettingsService;
import com.aibook.service.OperationLogService;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.test.util.ReflectionTestUtils;

class CrawlerTaskShutdownTest {
    @Test
    void contextCloseWaitsForWorkerCleanupBeforeDestroyingDatabase() throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        CrawlerTaskRepository tasks = mock(CrawlerTaskRepository.class);
        CrawlerTaskService service = service(context, tasks);
        ThreadPoolExecutor workers = (ThreadPoolExecutor)
                ReflectionTestUtils.getField(service, "executor");
        workers.setCorePoolSize(1);
        workers.setMaximumPoolSize(1);
        CloseProbe database = new CloseProbe();
        context.getBeanFactory().registerSingleton("crawlerTaskService", service);
        context.registerBean("databaseResource", CloseProbe.class, () -> database);
        context.refresh();

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        CountDownLatch releaseCleanup = new CountDownLatch(1);
        AtomicBoolean savedWhileDatabaseOpen = new AtomicBoolean();
        AtomicBoolean queuedTaskRan = new AtomicBoolean();
        ExecutorService closer = Executors.newSingleThreadExecutor();
        try {
            workers.execute(new TestJob(() -> {
                started.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException exception) {
                    interrupted.countDown();
                    try {
                        releaseCleanup.await();
                        savedWhileDatabaseOpen.set(!database.closed.get());
                    } catch (InterruptedException unexpected) {
                        Thread.currentThread().interrupt();
                    }
                }
            }));
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            workers.execute(new TestJob(() -> queuedTaskRan.set(true)));

            Future<?> closing = closer.submit(context::close);
            assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(closing.isDone()).isFalse();
            assertThat(database.closed).isFalse();
            releaseCleanup.countDown();
            closing.get(3, TimeUnit.SECONDS);

            assertThat(savedWhileDatabaseOpen).isTrue();
            assertThat(database.closed).isTrue();
            assertThat(workers.isTerminated()).isTrue();
            assertThat(queuedTaskRan).isFalse();
            service.shutdown();
            service.run("should-not-start");
            verifyNoInteractions(tasks);
        } finally {
            releaseCleanup.countDown();
            context.close();
            service.shutdown();
            closer.shutdownNow();
        }
    }

    @Test
    void unrelatedContextCloseDoesNotStopCrawler() {
        ApplicationContext owner = mock(ApplicationContext.class);
        CrawlerTaskService service = service(owner, mock(CrawlerTaskRepository.class));
        ThreadPoolExecutor workers = (ThreadPoolExecutor)
                ReflectionTestUtils.getField(service, "executor");
        try {
            service.onContextClosed(new ContextClosedEvent(mock(ApplicationContext.class)));
            assertThat(workers.isShutdown()).isFalse();
            service.onContextClosed(new ContextClosedEvent(owner));
            assertThat(workers.isTerminated()).isTrue();
        } finally {
            service.shutdown();
        }
    }

    private CrawlerTaskService service(ApplicationContext context, CrawlerTaskRepository tasks) {
        return new CrawlerTaskService(mock(CrawlerSiteRepository.class),
                mock(CrawlerDiscoveryPageRepository.class), mock(CrawlerBookRepository.class),
                mock(CrawlerChapterRepository.class), tasks, mock(CrawlerScanResultRepository.class),
                mock(CrawlerTaskLogRepository.class), mock(CrawlerManagementService.class),
                mock(CrawlerChapterAttemptMetricService.class), mock(OperationLogService.class),
                mock(CrawlerExportService.class), mock(CrawlerHttpClient.class), List.of(),
                context, mock(CrawlerSettingsService.class));
    }

    private record TestJob(Runnable action) implements Runnable, Comparable<TestJob> {
        @Override
        public void run() {
            action.run();
        }

        @Override
        public int compareTo(TestJob other) {
            return 0;
        }
    }

    static final class CloseProbe implements DisposableBean {
        final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public void destroy() {
            closed.set(true);
        }
    }
}
