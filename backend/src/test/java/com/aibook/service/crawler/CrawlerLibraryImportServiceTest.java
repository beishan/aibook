package com.aibook.service.crawler;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aibook.model.entity.CrawlerBook;
import com.aibook.model.entity.User;
import com.aibook.repository.CrawlerBookRepository;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.web.server.ResponseStatusException;

class CrawlerLibraryImportServiceTest {
    private final User user = User.builder().id(1L).build();
    private final CrawlerExportService exports = mock(CrawlerExportService.class);
    private final CrawlerBookRepository books = mock(CrawlerBookRepository.class);
    private final CrawlerLibraryImportService service = new CrawlerLibraryImportService(exports, books);
    private final List<String> formats = List.of("STRUCTURED");

    @Test
    void marksFailureForContentValidationAndPreservesOriginalError() {
        var failure = new ResponseStatusException(HttpStatus.CONFLICT, "没有可用正文");
        when(exports.importLibrary(user, 3L, formats)).thenThrow(failure);

        assertThatThrownBy(() -> service.importLibrary(user, 3L, formats)).isSameAs(failure);

        var order = inOrder(exports, books);
        order.verify(exports).importLibrary(user, 3L, formats);
        order.verify(books).updateImportFailure(3L, user, CrawlerBook.ImportStatus.FAILED);
    }

    @Test
    void marksUnexpectedFailuresAndCanRetrySuccessfully() {
        when(exports.importLibrary(user, 3L, formats))
                .thenThrow(new IllegalStateException("database commit failed")).thenReturn(101L);
        assertThatThrownBy(() -> service.importLibrary(user, 3L, formats))
                .hasMessage("database commit failed");
        assertThat(service.importLibrary(user, 3L, formats)).isEqualTo(101L);
        verify(books, times(1)).updateImportFailure(3L, user, CrawlerBook.ImportStatus.FAILED);
    }

    @Test
    void successNeverWritesFailedStatus() {
        when(exports.importLibrary(user, 3L, formats)).thenReturn(101L);
        assertThat(service.importLibrary(user, 3L, formats)).isEqualTo(101L);
        verifyNoInteractions(books);
    }

    @Test
    void statusWriteFailureDoesNotHideImportError() {
        var failure = new IllegalStateException("import failed");
        var statusFailure = new IllegalStateException("database unavailable");
        when(exports.importLibrary(user, 3L, formats)).thenThrow(failure);
        when(books.updateImportFailure(3L, user, CrawlerBook.ImportStatus.FAILED)).thenThrow(statusFailure);
        assertThatThrownBy(() -> service.importLibrary(user, 3L, formats)).isSameAs(failure);
        assertThat(failure.getSuppressed()).containsExactly(statusFailure);
    }

    @Test
    void failedStatusCommitsInANewTransactionAfterImportRollsBack() {
        List<String> events = new ArrayList<>();
        var transactions = new AbstractPlatformTransactionManager() {
            @Override protected Object doGetTransaction() { return new Object(); }
            @Override protected void doBegin(Object transaction, TransactionDefinition definition) {
                events.add("begin");
            }
            @Override protected void doCommit(DefaultTransactionStatus status) { events.add("commit"); }
            @Override protected void doRollback(DefaultTransactionStatus status) { events.add("rollback"); }
        };
        var persistedStatus = new AtomicReference<>(CrawlerBook.ImportStatus.READY);
        when(exports.importLibrary(user, 3L, formats)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { persistedStatus.set(CrawlerBook.ImportStatus.IMPORTED); }
            });
            throw new IllegalStateException("import failed");
        });
        when(books.updateImportFailure(3L, user, CrawlerBook.ImportStatus.FAILED)).thenAnswer(invocation -> {
            assertThat(persistedStatus.get()).isEqualTo(CrawlerBook.ImportStatus.READY);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            events.add("failed-status");
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { persistedStatus.set(CrawlerBook.ImportStatus.FAILED); }
            });
            return 1;
        });
        var attributes = new AnnotationTransactionAttributeSource();
        ProxyFactory exportProxy = new ProxyFactory(exports);
        exportProxy.setProxyTargetClass(true);
        exportProxy.addAdvice(new TransactionInterceptor(transactions, attributes));
        ProxyFactory repositoryProxy = new ProxyFactory(books);
        repositoryProxy.setInterfaces(CrawlerBookRepository.class);
        repositoryProxy.addAdvice(new TransactionInterceptor(transactions, attributes));
        var importer = new CrawlerLibraryImportService((CrawlerExportService) exportProxy.getProxy(),
                (CrawlerBookRepository) repositoryProxy.getProxy());

        assertThatThrownBy(() -> importer.importLibrary(user, 3L, formats)).hasMessage("import failed");
        assertThat(events).containsExactly("begin", "rollback", "begin", "failed-status", "commit");
        assertThat(persistedStatus.get()).isEqualTo(CrawlerBook.ImportStatus.FAILED);
    }
}
