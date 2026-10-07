package com.aibook.service.crawler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.aibook.model.entity.*;
import com.aibook.repository.*;
import com.aibook.service.CrawlerSettingsService;
import com.aibook.service.OperationLogService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class CrawlerTaskSubmissionTransactionTest {
    @Test
    void batchSubmissionWritesInFreshTransactionAfterCreationCommits() throws Exception {
        try (Fixture fixture = new Fixture()) {
            new TransactionTemplate(fixture.transactions).executeWithoutResult(status -> {
                assertThat(fixture.service.batchCrawl(fixture.user, List.of(3L))).hasSize(1);
                verify(fixture.tasks, never()).saveAll(any());
                verify(fixture.sites, never()).releaseExpiredManualFreezes(any());
            });

            assertThat(fixture.transactions.submissionCommitted.await(5, TimeUnit.SECONDS)).isTrue();
            verify(fixture.tasks).saveAll(any());
            verify(fixture.sites).releaseExpiredManualFreezes(any());
            assertThat(fixture.transactions.commits.get()).isEqualTo(2);
            assertThat(fixture.transactions.suspensions.get()).isZero();
            assertThat(fixture.created.getFirst().getQueueOrder()).isNotNull();
        }
    }

    @Test
    void rollbackDoesNotSubmitCreatedTasks() {
        try (Fixture fixture = new Fixture()) {
            new TransactionTemplate(fixture.transactions).executeWithoutResult(status -> {
                fixture.service.batchCrawl(fixture.user, List.of(3L));
                status.setRollbackOnly();
            });

            verify(fixture.tasks, never()).saveAll(any());
            verify(fixture.sites, never()).releaseExpiredManualFreezes(any());
            assertThat(fixture.transactions.commits.get()).isZero();
        }
    }

    @Test
    void singleSubmissionAlsoUsesFreshTransactionAfterCommit() throws Exception {
        try (Fixture fixture = new Fixture()) {
            when(fixture.tasks.findById(any())).thenAnswer(invocation ->
                    fixture.created.stream().findFirst());
            new TransactionTemplate(fixture.transactions).executeWithoutResult(status -> {
                fixture.service.continueBook(fixture.user, 3L);
                verify(fixture.sites, never()).releaseExpiredManualFreezes(any());
            });

            assertThat(fixture.transactions.submissionCommitted.await(5, TimeUnit.SECONDS)).isTrue();
            verify(fixture.sites).releaseExpiredManualFreezes(any());
            assertThat(fixture.transactions.commits.get()).isEqualTo(2);
            assertThat(fixture.transactions.suspensions.get()).isZero();
        }
    }

    @Test
    void slowDispatchDoesNotHoldUpRequestCommit() throws Exception {
        CountDownLatch dispatchEntered = new CountDownLatch(1);
        CountDownLatch releaseDispatch = new CountDownLatch(1);
        try (Fixture fixture = new Fixture()) {
            doAnswer(invocation -> {
                fixture.transactions.requireWritableTransaction();
                dispatchEntered.countDown();
                assertThat(releaseDispatch.await(5, TimeUnit.SECONDS)).isTrue();
                return 0;
            }).when(fixture.sites).releaseExpiredManualFreezes(any());
            try {
                new TransactionTemplate(fixture.transactions).executeWithoutResult(status ->
                        fixture.service.batchCrawl(fixture.user, List.of(3L)));
                assertThat(dispatchEntered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(fixture.transactions.commits.get()).isEqualTo(1);
            } finally {
                releaseDispatch.countDown();
            }
            assertThat(fixture.transactions.submissionCommitted.await(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final TrackingTransactions transactions = new TrackingTransactions();
        final User user = User.builder().id(1L).build();
        final CrawlerSiteRepository sites = mock(CrawlerSiteRepository.class);
        final CrawlerTaskRepository tasks = mock(CrawlerTaskRepository.class);
        final List<CrawlerTask> created = new ArrayList<>();
        final CrawlerTaskService service;

        Fixture() {
            CrawlerSite site = CrawlerSite.builder().id(2L).user(user).enabled(true).build();
            site.attachRule(CrawlerSiteRule.builder().build());
            CrawlerBook book = CrawlerBook.builder().id(3L).site(site).bookName("示例书").build();
            CrawlerBookRepository books = mock(CrawlerBookRepository.class);
            CrawlerManagementService management = mock(CrawlerManagementService.class);
            when(books.findByIdInAndSiteUser(any(), any())).thenReturn(List.of(book));
            when(management.ownedBook(user, 3L)).thenReturn(book);
            when(management.taskView(any())).thenCallRealMethod();
            when(tasks.save(any())).thenAnswer(invocation -> {
                transactions.requireWritableTransaction();
                CrawlerTask task = invocation.getArgument(0);
                if (task.getId() == null) task.setId(UUID.randomUUID().toString());
                if (!created.contains(task)) created.add(task);
                return task;
            });
            when(tasks.findAllById(any())).thenReturn(created);
            when(tasks.saveAll(any())).thenAnswer(invocation -> {
                transactions.requireWritableTransaction();
                return invocation.getArgument(0);
            });
            when(sites.releaseExpiredManualFreezes(any())).thenAnswer(invocation -> {
                transactions.requireWritableTransaction();
                return 0;
            });
            service = new CrawlerTaskService(sites, mock(CrawlerDiscoveryPageRepository.class),
                    books, mock(CrawlerChapterRepository.class), tasks,
                    mock(CrawlerScanResultRepository.class), mock(CrawlerTaskLogRepository.class),
                    management, mock(CrawlerChapterAttemptMetricService.class),
                    mock(OperationLogService.class), mock(CrawlerExportService.class),
                    mock(CrawlerHttpClient.class), List.of(), mock(ApplicationContext.class),
                    mock(CrawlerSettingsService.class));
            ReflectionTestUtils.setField(service, "transactionManager", transactions);
            // 空队列保留派发入口的更新查询，但不会启动真实采集线程。
            ReflectionTestUtils.setField(service, "taskQueueRepository",
                    mock(CrawlerTaskQueueRepository.class));
        }

        @Override
        public void close() {
            service.shutdown();
        }
    }

    /** 使用 Spring 的真实提交回调及挂起机制，拒绝已提交资源上的写入。 */
    private static final class TrackingTransactions extends AbstractPlatformTransactionManager {
        private final ThreadLocal<Resource> current = new ThreadLocal<>();
        final AtomicInteger commits = new AtomicInteger();
        final AtomicInteger suspensions = new AtomicInteger();
        final CountDownLatch submissionCommitted = new CountDownLatch(1);

        void requireWritableTransaction() {
            assertThat(current.get()).isNotNull();
            assertThat(current.get().committed).as("更新必须使用尚未提交的事务").isFalse();
        }

        @Override
        protected Object doGetTransaction() {
            return new Holder(current.get());
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return ((Holder) transaction).resource != null;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            Resource resource = new Resource();
            ((Holder) transaction).resource = resource;
            current.set(resource);
        }

        @Override
        protected Object doSuspend(Object transaction) {
            Resource resource = current.get();
            current.remove();
            ((Holder) transaction).resource = null;
            suspensions.incrementAndGet();
            return resource;
        }

        @Override
        protected void doResume(Object transaction, Object suspendedResources) {
            current.set((Resource) suspendedResources);
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            ((Holder) status.getTransaction()).resource.committed = true;
            if (commits.incrementAndGet() == 2) submissionCommitted.countDown();
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            current.remove();
        }

        private static final class Resource {
            boolean committed;
        }

        private static final class Holder {
            Resource resource;

            Holder(Resource resource) {
                this.resource = resource;
            }
        }
    }
}
