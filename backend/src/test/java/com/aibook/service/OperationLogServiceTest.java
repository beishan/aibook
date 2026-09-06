package com.aibook.service;

import com.aibook.model.entity.OperationLog;
import com.aibook.model.entity.User;
import com.aibook.repository.OperationLogRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
}
