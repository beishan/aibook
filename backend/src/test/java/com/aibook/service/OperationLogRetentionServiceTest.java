package com.aibook.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import com.aibook.dto.OperationLogRetentionDTO;
import com.aibook.dto.OperationLogRetentionDTO.Rule;
import com.aibook.model.entity.OperationLog;
import com.aibook.model.entity.OperationLogRetentionPolicy;
import com.aibook.model.entity.User;
import com.aibook.repository.OperationLogRepository;
import com.aibook.repository.OperationLogRetentionPolicyRepository;
import com.aibook.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OperationLogRetentionServiceTest {
    private final OperationLogRetentionPolicyRepository policies = mock(OperationLogRetentionPolicyRepository.class);
    private final OperationLogRepository logs = mock(OperationLogRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final OperationLogRetentionService service = new OperationLogRetentionService(policies, logs, users);
    private final User user = User.builder().id(1L).username("owner").build();

    @Test
    void returnsEveryTypeWithUnconfiguredDefaultsAndExplicitForever() {
        when(policies.findByUser(user)).thenReturn(List.of(policy(OperationLog.Action.OPEN_BOOK, 0)));
        var settings = service.getSettings(user);
        assertThat(settings.rules()).hasSize(OperationLog.Action.values().length);
        assertThat(settings.rules()).filteredOn(rule -> rule.action().equals("OPEN_BOOK"))
                .extracting(Rule::retentionDays).containsExactly(0);
        assertThat(settings.rules()).filteredOn(rule -> !rule.action().equals("OPEN_BOOK"))
                .allSatisfy(rule -> assertThat(rule.retentionDays()).isNull());
        assertThat(settings.rules()).allSatisfy(rule -> assertThat(rule.label()).isNotBlank());
    }

    @Test
    void savesPoliciesInAccountAndClearsUnconfiguredRules() {
        lockOwner();
        var previous = policy(OperationLog.Action.OPEN_BOOK, 7);
        when(policies.findByUser(user)).thenReturn(List.of(previous));
        service.updateSettings(user, new OperationLogRetentionDTO(List.of(
                rule("OPEN_BOOK", null), rule("CRAWLER_TASK", 2), rule("DELETE_BOOK", 0))));
        verify(policies).delete(previous);
        ArgumentCaptor<OperationLogRetentionPolicy> captured = ArgumentCaptor.forClass(OperationLogRetentionPolicy.class);
        verify(policies, times(2)).save(captured.capture());
        assertThat(captured.getAllValues()).allSatisfy(value -> assertThat(value.getUser()).isSameAs(user));
        assertThat(captured.getAllValues()).extracting(OperationLogRetentionPolicy::getRetentionDays)
                .containsExactlyInAnyOrder(0, 2);
        verify(users).findByIdForLogPolicyUpdate(1L);
        verify(policies).flush();
    }

    @Test
    void rejectsInvalidTypesDuplicateNullAndOutOfRangeValuesBeforeWriting() {
        for (OperationLogRetentionDTO settings : List.of(
                new OperationLogRetentionDTO(List.of(rule("BAD", 2))),
                new OperationLogRetentionDTO(List.of(rule("OPEN_BOOK", -1))),
                new OperationLogRetentionDTO(List.of(rule("OPEN_BOOK", 3651))),
                new OperationLogRetentionDTO(List.of(rule("OPEN_BOOK", null), rule("OPEN_BOOK", 2))),
                new OperationLogRetentionDTO(java.util.Arrays.asList((Rule) null)))) {
            assertThatThrownBy(() -> service.updateSettings(user, settings))
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        }
        assertThatThrownBy(() -> service.updateSettings(user, null)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(users, policies, logs);
    }

    @Test
    void cleanupUsesSavedPolicyOwnerAndExactRollingCutoffsAndSkipsForever() {
        lockOwner();
        LocalDateTime now = LocalDateTime.of(2026, 10, 21, 12, 0);
        when(policies.findByUser(user)).thenReturn(List.of(
                policy(OperationLog.Action.OPEN_BOOK, 2),
                policy(OperationLog.Action.CRAWLER_TASK, 7),
                policy(OperationLog.Action.DELETE_BOOK, 0)));
        when(logs.deleteExpired(user, OperationLog.Action.OPEN_BOOK, now.minusDays(2))).thenReturn(3);
        when(logs.deleteExpired(user, OperationLog.Action.CRAWLER_TASK, now.minusDays(7))).thenReturn(4);
        assertThat(service.cleanupAt(user, now)).isEqualTo(7);
        verify(logs).deleteExpired(user, OperationLog.Action.OPEN_BOOK, now.minusHours(48));
        verify(logs).deleteExpired(user, OperationLog.Action.CRAWLER_TASK, now.minusDays(7));
        verifyNoMoreInteractions(logs);
    }

    @Test
    void unconfiguredAndPermanentPoliciesNeverDeleteAnything() {
        lockOwner();
        when(policies.findByUser(user)).thenReturn(List.of(), List.of(policy(OperationLog.Action.OPEN_BOOK, 0)));
        assertThat(service.cleanupAt(user, LocalDateTime.now())).isZero();
        assertThat(service.cleanupAt(user, LocalDateTime.now())).isZero();
        verifyNoInteractions(logs);
    }

    private Rule rule(String action, Integer days) {
        return new Rule(action, "ignored client label", days);
    }

    private OperationLogRetentionPolicy policy(OperationLog.Action action, Integer days) {
        return OperationLogRetentionPolicy.builder().id((long) action.ordinal() + 1)
                .user(user).action(action).retentionDays(days).build();
    }

    private void lockOwner() {
        when(users.findByIdForLogPolicyUpdate(1L)).thenReturn(Optional.of(user));
    }
}
