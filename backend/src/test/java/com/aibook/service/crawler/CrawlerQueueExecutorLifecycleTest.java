package com.aibook.service.crawler;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aibook.dto.crawler.CrawlerDtos.QueueExecutorPayload;
import com.aibook.model.entity.*;
import com.aibook.repository.*;
import com.aibook.service.CrawlerSettingsService;
import com.aibook.service.ProxySettingsService;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CrawlerQueueExecutorLifecycleTest {
    private final CrawlerTaskQueueRepository queues = mock(CrawlerTaskQueueRepository.class);
    private final CrawlerQueueExecutorRepository executors = mock(CrawlerQueueExecutorRepository.class);
    private final CrawlerQueueExecutorProxyRepository bindings = mock(CrawlerQueueExecutorProxyRepository.class);
    private final CrawlerQueueProxyCooldownRepository cooldowns = mock(CrawlerQueueProxyCooldownRepository.class);
    private final ProxySettingsService proxies = mock(ProxySettingsService.class);
    private final CrawlerMihomoService mihomo = mock(CrawlerMihomoService.class);
    private final CrawlerQueueExecutorService service = new CrawlerQueueExecutorService(
            queues, executors, bindings, cooldowns, proxies, mock(CrawlerSettingsService.class), mihomo);
    private final User user = User.builder().id(1L).build();
    private final CrawlerTaskQueue queue = CrawlerTaskQueue.builder().id(2L).user(user)
            .executorsInitialized(true).maxConcurrentTasks(1).build();
    private final CrawlerQueueExecutor executor = CrawlerQueueExecutor.builder().id(3L).queue(queue)
            .name("示例执行器").build();
    private final List<CrawlerQueueExecutor> rows = new ArrayList<>();

    @BeforeEach
    void setup() {
        rows.add(executor);
        when(queues.findById(2L)).thenReturn(Optional.of(queue));
        when(executors.findById(3L)).thenReturn(Optional.of(executor));
        when(executors.findByQueueIdOrderBySortOrderAscIdAsc(2L)).thenAnswer(call -> new ArrayList<>(rows));
        when(executors.save(any())).thenAnswer(call -> {
            CrawlerQueueExecutor row = call.getArgument(0);
            if (row.getId() == null) { row.setId(4L); rows.add(row); }
            return row;
        });
        doAnswer(call -> { rows.remove(call.getArgument(0)); return null; }).when(executors).delete(any());
        when(executors.findByDeletionPendingTrue()).thenAnswer(call -> rows.stream()
                .filter(row -> Boolean.TRUE.equals(row.getDeletionPending())).toList());
        when(proxies.crawlerProxies()).thenReturn(List.of());
        when(cooldowns.findByQueueId(2L)).thenReturn(List.of());
    }

    private QueueExecutorPayload payload() {
        return new QueueExecutorPayload("新执行器", null, true, CrawlerQueueExecutor.ProxyMode.DEFAULT,
                CrawlerQueueExecutor.SelectionStrategy.ORDERED, null, List.of());
    }

    @Test
    void creatingEnabledExecutorDoesNotAssignOrIncreaseConcurrencyUntilManualAddition() {
        var created = service.create(user, 2L, payload());
        assertThat(created.enabled()).isTrue();
        assertThat(created.assigned()).isFalse();
        assertThat(queue.getMaxConcurrentTasks()).isEqualTo(1);
        assertThat(service.enabledExecutors(2L)).containsExactly(executor);
        when(executors.findById(4L)).thenReturn(Optional.of(rows.getLast()));
        var assigned = service.assign(user, 2L, 4L);
        assertThat(assigned.assigned()).isTrue();
        assertThat(queue.getMaxConcurrentTasks()).isEqualTo(2);
        assertThat(service.enabledExecutors(2L)).hasSize(2);
    }

    @Test
    void updatingUnassignedExecutorDoesNotImplicitlyAddIt() {
        executor.setAssigned(false);
        service.update(user, 2L, 3L, payload());
        assertThat(executor.getAssigned()).isFalse();
        assertThat(service.enabledExecutors(2L)).isEmpty();
        assertThat(queue.getMaxConcurrentTasks()).isZero();
    }

    @Test
    void deletingActiveExecutorKeepsCurrentChapterAndNextChapterConfigurationUntilJobEnds() throws Exception {
        service.delete(user, 2L, 3L, true);
        assertThat(executor.getDeletionPending()).isTrue();
        assertThat(service.enabledExecutors(2L)).isEmpty();
        assertThat(queue.getMaxConcurrentTasks()).isZero();
        verify(executors, never()).delete(any());
        verify(bindings, never()).deleteByExecutorId(any());
        verifyNoInteractions(mihomo);
        try (var route = service.bind(2L, 3L)) {
            try (var chapter = service.beginChapter(null)) { assertThat(service.hasBoundExecutor()).isTrue(); }
            try (var nextChapter = service.beginChapter(null)) { assertThat(service.hasBoundExecutor()).isTrue(); }
        }
        service.finalizePendingDeletes(Set.of(3L));
        verify(executors, never()).delete(any());
        service.finalizePendingDeletes(Set.of());
        verify(executors).delete(executor);
        verify(bindings).deleteByExecutorId(3L);
        verify(mihomo).delete(3L);
        assertThat(rows).isEmpty();
    }

    @Test
    void pendingExecutorCannotBeReenabledOrAssigned() {
        service.delete(user, 2L, 3L, true);
        assertThatThrownBy(() -> service.assign(user, 2L, 3L))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.update(user, 2L, 3L, payload()))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(service.enabledExecutors(2L)).isEmpty();
    }

    @Test
    void defaultExecutorCanBeDeletedAndEmptyQueueDoesNotRecreateExecutors() {
        executor.setDefaultExecutor(true);
        service.delete(user, 2L, 3L, false);
        assertThat(rows).isEmpty();
        service.ensureQueueExecutors(queue);
        verify(executors, never()).saveAll(argThat(list -> list.iterator().hasNext()));
        assertThat(queue.getMaxConcurrentTasks()).isZero();
    }

    @Test
    void legacyExecutorsRemainAssignedAndOldQueueIsMarkedInitialized() {
        executor.setAssigned(null);
        executor.setDeletionPending(null);
        queue.setExecutorsInitialized(null);
        service.ensureQueueExecutors(queue);
        assertThat(queue.getExecutorsInitialized()).isTrue();
        assertThat(service.enabledExecutors(2L)).containsExactly(executor);
    }

    @Test
    void otherAccountCannotAssignOrDeleteExecutor() {
        User other = User.builder().id(8L).build();
        assertThatThrownBy(() -> service.assign(other, 2L, 3L)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.delete(other, 2L, 3L, true)).isInstanceOf(ResponseStatusException.class);
        assertThat(executor.getDeletionPending()).isFalse();
    }
}
