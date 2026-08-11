package com.aibook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibook.dto.TrashCleanupSettingsDTO;
import com.aibook.model.entity.User;
import com.aibook.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrashCleanupServiceTest {

    @Test
    void updatesAndReadsRetentionSettings() {
        UserRepository userRepository = mock(UserRepository.class);
        BookService bookService = mock(BookService.class);
        User user = User.builder().id(1L).enabled(true).build();
        when(userRepository.save(user)).thenReturn(user);
        TrashCleanupService service = new TrashCleanupService(userRepository, bookService);

        TrashCleanupSettingsDTO result =
                service.updateSettings(user, new TrashCleanupSettingsDTO(30));

        assertThat(result.retentionDays()).isEqualTo(30);
        assertThat(user.getTrashRetentionDays()).isEqualTo(30);
    }

    @Test
    void rejectsUnsupportedRetentionDays() {
        TrashCleanupService service = new TrashCleanupService(
                mock(UserRepository.class), mock(BookService.class));

        assertThatThrownBy(() -> service.updateSettings(
                User.builder().build(), new TrashCleanupSettingsDTO(14)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void purgesOnlyEnabledUsersWithConfiguredRetention() {
        UserRepository userRepository = mock(UserRepository.class);
        BookService bookService = mock(BookService.class);
        User enabled = User.builder()
                .id(1L).enabled(true).trashRetentionDays(15).build();
        User disabled = User.builder()
                .id(2L).enabled(false).trashRetentionDays(30).build();
        when(userRepository.findByTrashRetentionDaysGreaterThan(0))
                .thenReturn(List.of(enabled, disabled));
        TrashCleanupService service = new TrashCleanupService(userRepository, bookService);
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 12, 0);

        service.runCleanup(now);

        verify(bookService).purgeExpiredTrash(enabled, now.minusDays(15));
        verify(bookService, never()).purgeExpiredTrash(disabled, now.minusDays(30));
    }
}
