package com.aibook.service;

import com.aibook.model.entity.OperationLog;
import com.aibook.model.entity.User;
import com.aibook.repository.OperationLogRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import java.util.List;

class OperationLogServiceTest {
    @Test
    void searchesOwnedLogsWithLiteralWildcardsAndMapsRetention() {
        var repository = mock(OperationLogRepository.class);
        var service = new OperationLogService(repository);
        var owner = User.builder().id(1L).build();
        var pageable = PageRequest.of(0, 20);
        var log = OperationLog.builder().id(9L).user(owner).action(OperationLog.Action.OPEN_BOOK)
                .description("打开").retained(true).build();
        when(repository.search(owner, OperationLog.Action.OPEN_BOOK, "%a!%!_!!%", pageable))
                .thenReturn(new PageImpl<>(List.of(log)));
        var result = service.getLogs(owner, pageable, "OPEN_BOOK", " A%_! ");
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).isRetained()).isTrue();
        verify(repository).search(owner, OperationLog.Action.OPEN_BOOK, "%a!%!_!!%", pageable);
    }

    @Test
    void validatesSearchAndSupportsUnfilteredPagination() {
        var repository = mock(OperationLogRepository.class);
        var service = new OperationLogService(repository);
        var owner = User.builder().id(1L).build();
        var pageable = PageRequest.of(0, 20);
        when(repository.search(owner, null, "", pageable)).thenReturn(new PageImpl<>(List.of()));
        assertThat(service.getLogs(owner, pageable, null, "  ").isEmpty()).isTrue();
        assertThatThrownBy(() -> service.getLogs(owner, pageable, "BAD_TYPE", ""))
                .hasMessageContaining("无效的日志类型");
        assertThatThrownBy(() -> service.getLogs(owner, pageable, null, "a".repeat(201)))
                .hasMessageContaining("200");
    }

    @Test
    void retainsAndManuallyDeletesOnlyOwnedLogs() {
        var repository = mock(OperationLogRepository.class);
        var service = new OperationLogService(repository);
        var owner = User.builder().id(1L).build();
        when(repository.retain(owner, 5L)).thenReturn(1);
        when(repository.deleteOwned(owner, 5L)).thenReturn(1);
        service.retain(owner, 5L);
        service.delete(owner, 5L);
        verify(repository).retain(owner, 5L);
        verify(repository).deleteOwned(owner, 5L);
        assertThatThrownBy(() -> service.retain(owner, 99L)).hasMessageContaining("日志不存在");
        assertThatThrownBy(() -> service.delete(owner, 99L)).hasMessageContaining("日志不存在");
        when(repository.existsByUserAndRetainedTrue(owner)).thenReturn(true);
        assertThat(service.hasRetainedLogs(owner)).isTrue();
        verify(repository, never()).deleteById(anyLong());
    }

    @Test
    void onlyAdministratorsCanReadAndManuallyManageArchivedLogs() {
        var repository = mock(OperationLogRepository.class);
        var service = new OperationLogService(repository);
        var admin = User.builder().id(1L).role(User.Role.ADMIN).build();
        var regular = User.builder().id(2L).role(User.Role.USER).build();
        var page = PageRequest.of(0, 20);
        var archived = OperationLog.builder().id(9L).user(null).ownerUserId(3L)
                .ownerUsername("deleted-reader").action(OperationLog.Action.OPEN_BOOK)
                .description("old entry").retained(true).build();
        when(repository.searchWithArchived(admin, null, "", page))
                .thenReturn(new PageImpl<>(List.of(archived)));
        var result = service.getLogs(admin, page, null, null).getContent().get(0);
        assertThat(result.isArchived()).isTrue();
        assertThat(result.getOwnerUserId()).isEqualTo(3L);
        assertThat(result.getOwnerUsername()).isEqualTo("deleted-reader");
        assertThat(result.isRetained()).isTrue();
        when(repository.retainArchived(9L)).thenReturn(1);
        when(repository.deleteArchived(9L)).thenReturn(1);
        service.retain(admin, 9L);
        service.delete(admin, 9L);
        assertThatThrownBy(() -> service.retain(regular, 9L)).hasMessageContaining("不存在");
        assertThatThrownBy(() -> service.delete(regular, 9L)).hasMessageContaining("不存在");
        verify(repository).retainArchived(9L);
        verify(repository).deleteArchived(9L);
        when(repository.search(regular, null, "", page)).thenReturn(new PageImpl<>(List.of()));
        assertThat(service.getLogs(regular, page, null, null).isEmpty()).isTrue();
        verify(repository, never()).searchWithArchived(eq(regular), any(), any(), any());
    }

    @Test
    void recordsCrawlerSummaryWithExternalBookContext() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        OperationLogService service = new OperationLogService(repository);
        User user = User.builder().id(1L).username("owner").build();

        service.recordEntry(user, OperationLog.Action.CRAWLER_TASK, null, "示例书籍",
                "采集任务完毕：示例书籍", "任务ID：task-1；进度：10/10");

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(repository).save(captor.capture());
        OperationLog saved = captor.getValue();
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getOwnerUserId()).isEqualTo(1L);
        assertThat(saved.getOwnerUsername()).isEqualTo("owner");
        assertThat(saved.getAction()).isEqualTo(OperationLog.Action.CRAWLER_TASK);
        assertThat(saved.getBookId()).isNull();
        assertThat(saved.getBookTitle()).isEqualTo("示例书籍");
        assertThat(saved.getDescription()).isEqualTo("采集任务完毕：示例书籍");
        assertThat(saved.getDetails()).contains("task-1", "10/10");
    }
}
