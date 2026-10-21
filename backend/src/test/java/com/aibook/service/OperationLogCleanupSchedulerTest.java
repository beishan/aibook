package com.aibook.service;

import static org.mockito.Mockito.*;
import com.aibook.model.entity.User;
import com.aibook.repository.OperationLogRetentionPolicyRepository;
import com.aibook.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OperationLogCleanupSchedulerTest {
    @Test
    void cleansConfiguredAccountsIndependentlyAndSkipsDeletedUsers() {
        var policies = mock(OperationLogRetentionPolicyRepository.class);
        var users = mock(UserRepository.class);
        var retention = mock(OperationLogRetentionService.class);
        var first = User.builder().id(1L).build();
        var second = User.builder().id(2L).build();
        when(policies.findExpiringPolicyUserIds()).thenReturn(List.of(1L, 2L, 3L));
        when(users.findById(1L)).thenReturn(Optional.of(first));
        when(users.findById(2L)).thenReturn(Optional.of(second));
        when(users.findById(3L)).thenReturn(Optional.empty());
        when(retention.cleanup(first)).thenThrow(new IllegalStateException("isolated failure"));
        when(retention.cleanup(second)).thenReturn(4);
        new OperationLogCleanupScheduler(policies, users, retention).cleanup();
        verify(retention).cleanup(first);
        verify(retention).cleanup(second);
        verifyNoMoreInteractions(retention);
    }
}
