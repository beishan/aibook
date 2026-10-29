package com.aibook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.aibook.model.entity.OperationLog;
import com.aibook.model.entity.User;
import com.aibook.repository.OperationLogRepository;
import com.aibook.repository.UserRepository;
import java.sql.SQLException;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class SystemErrorLogServiceTest {

    @Test
    void capturesConstraintAndAccountWithoutSensitiveExceptionMessages() {
        var logs = mock(OperationLogRepository.class);
        var users = mock(UserRepository.class);
        var user = new User();
        user.setId(7L);
        user.setUsername("reader");
        when(users.findByUsername("reader")).thenReturn(Optional.of(user));
        var sql = new SQLException("Failing row contains password=secret", "23514");
        var cause = new ConstraintViolationException("token=secret", sql, "crawler_tasks_priority_check");
        new SystemErrorLogService(logs, users).record("incident-1", "reader", "POST",
                "/api/crawler/sites/{id}/scan", new RuntimeException("secret", cause));

        var entry = ArgumentCaptor.forClass(OperationLog.class);
        verify(logs).saveAndFlush(entry.capture());
        assertThat(entry.getValue().getAction()).isEqualTo(OperationLog.Action.SYSTEM_ERROR);
        assertThat(entry.getValue().getUser()).isSameAs(user);
        assertThat(entry.getValue().getOwnerUserId()).isEqualTo(7L);
        assertThat(entry.getValue().getDetails()).contains("incident-1", "POST",
                "23514", "crawler_tasks_priority_check", "ConstraintViolationException")
                .doesNotContain("secret", "Failing row");
    }

    @Test
    void anonymousErrorsAreVisibleAsSystemEntriesAndDiagnosticsAreBounded() {
        var logs = mock(OperationLogRepository.class);
        var users = mock(UserRepository.class);
        Throwable error = new IllegalStateException("private");
        for (int index = 0; index < 30; index++) error = new RuntimeException("private", error);
        new SystemErrorLogService(logs, users).record("incident-2", null, "GET", "/api/test", error);
        var entry = ArgumentCaptor.forClass(OperationLog.class);
        verify(logs).saveAndFlush(entry.capture());
        verifyNoInteractions(users);
        assertThat(entry.getValue().getUser()).isNull();
        assertThat(entry.getValue().getDetails().length()).isLessThanOrEqualTo(24000);
        assertThat(entry.getValue().getDetails()).doesNotContain("private");
    }

    @Test
    void usesIndependentTransactionToSurviveBusinessRollback() throws Exception {
        var method = SystemErrorLogService.class.getMethod("record", String.class,
                String.class, String.class, String.class, Throwable.class);
        assertThat(method.getAnnotation(Transactional.class).propagation())
                .isEqualTo(Propagation.REQUIRES_NEW);
    }
}
