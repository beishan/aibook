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
import com.aibook.repository.OperationLogCleanupScheduleRepository;
import com.aibook.model.entity.OperationLogCleanupSchedule;
import com.aibook.dto.OperationLogRetentionDTO.Schedule;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OperationLogRetentionServiceTest {
    private final OperationLogRetentionPolicyRepository policies = mock(OperationLogRetentionPolicyRepository.class);
    private final OperationLogRepository logs = mock(OperationLogRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final OperationLogCleanupScheduleRepository schedules = mock(OperationLogCleanupScheduleRepository.class);
    private final OperationLogRetentionService service = new OperationLogRetentionService(policies, logs, users, schedules);
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

    @Test
    void schedulingDefaultsPreserveHourlyBehaviorAndOldClientsKeepDisabledState() {
        lockOwner();
        var schedule = OperationLogCleanupSchedule.builder().user(user).userId(1L).enabled(false).build();
        when(schedules.findById(1L)).thenReturn(Optional.of(schedule));
        when(policies.findByUser(user)).thenReturn(List.of(policy(OperationLog.Action.OPEN_BOOK, 2)));
        service.updateSettings(user, new OperationLogRetentionDTO(List.of(rule("OPEN_BOOK", 2))));
        assertThat(schedule.isEnabled()).isFalse();
        assertThat(schedule.getNextRunAt()).isNull();
        assertThat(service.getSettings(user).schedule().enabled()).isFalse();
        verify(schedules).save(schedule);
    }

    @Test
    void rejectsMalformedSchedulesBeforeMutatingPolicies() {
        for (Schedule schedule : List.of(
                new Schedule(null, "DAILY", 3, 30, 1, null, null),
                new Schedule(true, "INVALID", 3, 30, 1, null, null),
                new Schedule(true, "DAILY", 24, 30, 1, null, null),
                new Schedule(true, "HOURLY", 3, 60, 1, null, null),
                new Schedule(true, "WEEKLY", 3, 30, 0, null, null))) {
            assertThatThrownBy(() -> service.updateSettings(user,
                    new OperationLogRetentionDTO(List.of(), schedule)))
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        }
        verifyNoInteractions(users, policies, logs, schedules);
    }

    @Test
    void savesAccountScheduleAndDoesNotTrustClientExecutionMetadata() {
        lockOwner();
        var last = LocalDateTime.of(2026, 10, 1, 3, 35);
        var stored = OperationLogCleanupSchedule.builder().userId(1L).user(user).lastRunAt(last).build();
        when(schedules.findById(1L)).thenReturn(Optional.of(stored));
        when(policies.findByUser(user)).thenReturn(List.of(policy(OperationLog.Action.OPEN_BOOK, 2)));
        var forged = LocalDateTime.of(2099, 1, 1, 0, 0);
        var request = new Schedule(true, "WEEKLY", 4, 12, 7, forged, forged);
        service.updateSettings(user, new OperationLogRetentionDTO(List.of(rule("OPEN_BOOK", 2)), request));
        assertThat(stored.getFrequency()).isEqualTo(OperationLogCleanupSchedule.Frequency.WEEKLY);
        assertThat(stored.getNextRunAt().getDayOfWeek()).isEqualTo(java.time.DayOfWeek.SUNDAY);
        assertThat(stored.getNextRunAt().getHour()).isEqualTo(4);
        assertThat(stored.getNextRunAt().getMinute()).isEqualTo(12);
        assertThat(stored.getNextRunAt()).isBefore(forged);
        assertThat(stored.getLastRunAt()).isEqualTo(last);
        assertThat(stored.getUserId()).isEqualTo(1L);
    }

    @Test
    void disabledScheduleDoesNotRunButManualCleanupStillWorks() {
        lockOwner();
        var now = LocalDateTime.of(2026, 10, 21, 12, 0);
        var schedule = OperationLogCleanupSchedule.builder().userId(1L).user(user)
                .enabled(false).nextRunAt(now.minusDays(1)).build();
        when(schedules.findById(1L)).thenReturn(Optional.of(schedule));
        when(policies.findByUser(user)).thenReturn(List.of(policy(OperationLog.Action.OPEN_BOOK, 2)));
        assertThat(service.runScheduledCleanupAt(user, now)).isZero();
        verifyNoInteractions(logs);
        when(logs.deleteExpired(user, OperationLog.Action.OPEN_BOOK, now.minusDays(2))).thenReturn(4);
        assertThat(service.cleanupAt(user, now)).isEqualTo(4);
        verify(schedules, never()).save(any());
    }

    @Test
    void duePlanRunsOnceThenPersistsNextFutureTimeIncludingAfterRestart() {
        lockOwner();
        var now = LocalDateTime.of(2026, 10, 21, 12, 35);
        var schedule = OperationLogCleanupSchedule.builder().userId(1L).user(user)
                .nextRunAt(now.minusDays(2)).build();
        when(schedules.findById(1L)).thenReturn(Optional.of(schedule));
        when(policies.findByUser(user)).thenReturn(List.of(policy(OperationLog.Action.OPEN_BOOK, 2)));
        when(logs.deleteExpired(user, OperationLog.Action.OPEN_BOOK, now.minusDays(2))).thenReturn(3);
        assertThat(service.runScheduledCleanupAt(user, now)).isEqualTo(3);
        assertThat(schedule.getLastRunAt()).isEqualTo(now);
        assertThat(schedule.getNextRunAt()).isEqualTo(now.plusHours(1));
        assertThat(service.runScheduledCleanupAt(user, now)).isZero();
        verify(logs).deleteExpired(user, OperationLog.Action.OPEN_BOOK, now.minusDays(2));
        verify(schedules).save(schedule);
    }

    @Test
    void missingScheduleInitializesWithoutImmediateDeletion() {
        lockOwner();
        var now = LocalDateTime.of(2026, 10, 21, 12, 40);
        when(policies.findByUser(user)).thenReturn(List.of(policy(OperationLog.Action.OPEN_BOOK, 2)));
        assertThat(service.runScheduledCleanupAt(user, now)).isZero();
        var saved = ArgumentCaptor.forClass(OperationLogCleanupSchedule.class);
        verify(schedules).save(saved.capture());
        assertThat(saved.getValue().getNextRunAt()).isEqualTo(now.withHour(13).withMinute(35));
        assertThat(saved.getValue().isEnabled()).isTrue();
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
