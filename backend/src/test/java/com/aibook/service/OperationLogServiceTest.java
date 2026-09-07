package com.aibook.service;

import com.aibook.model.entity.OperationLog;
import com.aibook.model.entity.User;
import com.aibook.repository.OperationLogRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationLogServiceTest {
    @Test
    void recordsCrawlerEntryWithExternalBookContext() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        OperationLogService service = new OperationLogService(repository);
        User user = User.builder().id(1L).username("owner").build();

        service.recordEntry(user, OperationLog.Action.CRAWLER_TASK, null, "示例书籍",
                "章节采集完毕：示例书籍", "任务ID：task-1；进度：1/10；预览：正文内容");

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(repository).save(captor.capture());
        OperationLog saved = captor.getValue();
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getAction()).isEqualTo(OperationLog.Action.CRAWLER_TASK);
        assertThat(saved.getBookId()).isNull();
        assertThat(saved.getBookTitle()).isEqualTo("示例书籍");
        assertThat(saved.getDescription()).isEqualTo("章节采集完毕：示例书籍");
        assertThat(saved.getDetails()).contains("task-1", "1/10", "正文内容");
    }

    @Test
    void loadsCrawlerLogsForTheRequestedBookAndSite() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        OperationLogService service = new OperationLogService(repository);
        User user = User.builder().id(1L).username("owner").build();
        OperationLog log = OperationLog.builder()
                .id(7L)
                .user(user)
                .action(OperationLog.Action.CRAWLER_TASK)
                .bookTitle("示例书籍")
                .description("章节采集完毕：示例书籍")
                .details("任务ID：task-1；网站：示例站点；进度：1/10")
                .createdAt(LocalDateTime.of(2026, 9, 7, 10, 30))
                .build();
        when(repository.findByUserAndActionAndBookTitleAndDetailsContaining(
                        eq(user),
                        eq(OperationLog.Action.CRAWLER_TASK),
                        eq("示例书籍"),
                        eq("网站：示例站点"),
                        any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));

        var result = service.getCrawlerLogs(user, "示例书籍", "示例站点", 100);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(7L);
        assertThat(result.getFirst().getDetails()).contains("task-1", "1/10");
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByUserAndActionAndBookTitleAndDetailsContaining(
                eq(user),
                eq(OperationLog.Action.CRAWLER_TASK),
                eq("示例书籍"),
                eq("网站：示例站点"),
                pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt")).isNotNull();
    }
}
