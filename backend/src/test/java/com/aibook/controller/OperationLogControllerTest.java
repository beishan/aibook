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
    void enforcesDescendingOrderAndSafePaginationBounds() {
        OperationLogService operationLogService = mock(OperationLogService.class);
        UserService userService = mock(UserService.class);
        Authentication authentication = mock(Authentication.class);
        User user = User.builder().id(1L).username("reader").build();
        Page<OperationLogDTO> result = new PageImpl<>(List.of());

        when(authentication.getName()).thenReturn("reader");
        when(userService.findByUsername("reader")).thenReturn(user);
        when(operationLogService.getLogs(eq(user), any()))
                .thenReturn(result);

        OperationLogController controller =
                new OperationLogController(operationLogService, userService);
        assertThat(controller.getLogs(authentication, -3, 500).getBody()).isSameAs(result);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(operationLogService).getLogs(eq(user), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(100);
        assertThat(pageable.getSort().getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(pageable.getSort().getOrderFor("id").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }
}
