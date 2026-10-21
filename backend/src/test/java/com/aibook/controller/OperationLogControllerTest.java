package com.aibook.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibook.dto.OperationLogDTO;
import com.aibook.model.entity.User;
import com.aibook.service.OperationLogService;
import com.aibook.service.OperationLogRetentionService;
import com.aibook.service.UserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;

class OperationLogControllerTest {

    @Test
    void mutationAndPolicyEndpointsAlwaysUseAuthenticatedAccount() {
        var service = mock(OperationLogService.class);
        var users = mock(UserService.class);
        var retention = mock(OperationLogRetentionService.class);
        var auth = mock(Authentication.class);
        var user = User.builder().id(1L).username("reader").build();
        when(auth.getName()).thenReturn("reader");
        when(users.findByUsername("reader")).thenReturn(user);
        var controller = new OperationLogController(service, users, retention);
        var request = new com.aibook.dto.OperationLogRetentionDTO(List.of());
        when(retention.getSettings(user)).thenReturn(request);
        when(retention.updateSettings(user, request)).thenReturn(request);
        when(retention.cleanup(user)).thenReturn(7);
        assertThat(controller.retention(auth)).isSameAs(request);
        assertThat(controller.updateRetention(auth, request)).isSameAs(request);
        assertThat(controller.cleanup(auth)).containsEntry("deletedCount", 7);
        assertThat(controller.retain(auth, 9L).getStatusCode().value()).isEqualTo(204);
        assertThat(controller.delete(auth, 9L).getStatusCode().value()).isEqualTo(204);
        verify(service).retain(user, 9L);
        verify(service).delete(user, 9L);
        verify(retention).cleanup(user);
    }

    @Test
    void enforcesDescendingOrderAndSafePaginationBounds() {
        OperationLogService operationLogService = mock(OperationLogService.class);
        UserService userService = mock(UserService.class);
        Authentication authentication = mock(Authentication.class);
        User user = User.builder().id(1L).username("reader").build();
        Page<OperationLogDTO> result = new PageImpl<>(List.of());

        when(authentication.getName()).thenReturn("reader");
        when(userService.findByUsername("reader")).thenReturn(user);
        when(operationLogService.getLogs(eq(user), any(), eq("CRAWLER_TASK"), eq("正文")))
                .thenReturn(result);

        OperationLogController controller =
                new OperationLogController(operationLogService, userService, mock(OperationLogRetentionService.class));
        assertThat(controller.getLogs(authentication, -3, 500, "CRAWLER_TASK", "正文").getBody()).isSameAs(result);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(operationLogService).getLogs(eq(user), pageableCaptor.capture(), eq("CRAWLER_TASK"), eq("正文"));
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(100);
        assertThat(pageable.getSort().getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(pageable.getSort().getOrderFor("id").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }
}
