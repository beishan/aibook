package com.aibook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibook.dto.ScheduledScanSettingsDTO;
import com.aibook.model.entity.ScanDirectory;
import com.aibook.model.entity.User;
import com.aibook.model.entity.UserPreference;
import com.aibook.repository.ScanDirectoryRepository;
import com.aibook.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduledScanServiceTest {

    @Test
    void startsEnabledDirectoriesAtConfiguredTimeOnlyOncePerMinute() {
        User user = User.builder()
                .id(1L)
                .username("reader")
                .enabled(true)
                .preferences(UserPreference.builder()
                        .scheduledScanEnabled(true)
                        .scheduledScanTime("03:15")
                        .build())
                .build();
        ScanDirectory enabledDirectory = ScanDirectory.builder()
                .id(10L)
                .path("/books/enabled")
                .enabled(true)
                .user(user)
                .build();
        ScanDirectory disabledDirectory = ScanDirectory.builder()
                .id(11L)
                .path("/books/disabled")
                .enabled(false)
                .user(user)
                .build();
        UserRepository userRepository = mock(UserRepository.class);
        ScanDirectoryRepository directoryRepository = mock(ScanDirectoryRepository.class);
        ScanDirectoryTaskService taskService = mock(ScanDirectoryTaskService.class);
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(directoryRepository.findByUser(user))
                .thenReturn(List.of(enabledDirectory, disabledDirectory));
        ScheduledScanService service =
                new ScheduledScanService(userRepository, directoryRepository, taskService);

        LocalDateTime dueTime = LocalDateTime.of(2026, 8, 11, 3, 15, 8);
        service.runDueScans(dueTime);
        service.runDueScans(dueTime.withSecond(50));

        verify(taskService).startScan(10L, user);
        verify(taskService, never()).startScan(11L, user);
    }

    @Test
    void skipsDisabledSettingAndUsersThatAreNotDue() {
        User disabledSchedule = User.builder()
                .id(1L)
                .enabled(true)
                .preferences(UserPreference.builder()
                        .scheduledScanEnabled(false)
                        .scheduledScanTime("03:15")
                        .build())
                .build();
        User laterSchedule = User.builder()
                .id(2L)
                .enabled(true)
                .preferences(UserPreference.builder()
                        .scheduledScanEnabled(true)
                        .scheduledScanTime("04:15")
                        .build())
                .build();
        UserRepository userRepository = mock(UserRepository.class);
        ScanDirectoryRepository directoryRepository = mock(ScanDirectoryRepository.class);
        ScanDirectoryTaskService taskService = mock(ScanDirectoryTaskService.class);
        when(userRepository.findAll()).thenReturn(List.of(disabledSchedule, laterSchedule));
        ScheduledScanService service =
                new ScheduledScanService(userRepository, directoryRepository, taskService);

        service.runDueScans(LocalDateTime.of(2026, 8, 11, 3, 15));

        verify(directoryRepository, never()).findByUser(disabledSchedule);
        verify(directoryRepository, never()).findByUser(laterSchedule);
        verify(taskService, never()).startScan(10L, disabledSchedule);
    }

    @Test
    void persistsSettingsAndRejectsInvalidTime() {
        User user = User.builder().id(1L).enabled(true).build();
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.save(user)).thenReturn(user);
        ScheduledScanService service = new ScheduledScanService(
                userRepository,
                mock(ScanDirectoryRepository.class),
                mock(ScanDirectoryTaskService.class));

        ScheduledScanSettingsDTO saved = service.updateSettings(
                user, new ScheduledScanSettingsDTO(false, "23:45"));

        assertThat(saved.enabled()).isFalse();
        assertThat(saved.time()).isEqualTo("23:45");
        assertThat(user.getScheduledScanEnabled()).isFalse();
        assertThat(user.getScheduledScanTime()).isEqualTo("23:45");
        assertThatThrownBy(() -> service.updateSettings(
                        user, new ScheduledScanSettingsDTO(true, "24:00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("扫描时间无效");
    }
}
