package com.aibook.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aibook.service.SystemErrorLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerMapping;

class GlobalExceptionHandlerSystemErrorTest {
    private final SystemErrorLogService logs = mock(SystemErrorLogService.class);
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(logs);

    @AfterEach
    void resetContext() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void recordsUnexpectedErrorWithAccountAndRouteButNoQueryString() {
        var request = new MockHttpServletRequest("POST", "/api/test/7");
        request.setQueryString("token=secret");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/test/{id}");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("reader", null, java.util.List.of()));
        var error = new RuntimeException("private database row");
        var response = handler.handleGeneral(error, new MockHttpServletResponse());
        String errorId = (String) response.getBody().get("errorId");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(errorId).isNotBlank();
        assertThat(response.getBody().toString()).doesNotContain("private database row", "secret");
        verify(logs).record(errorId, "reader", "POST", "/api/test/{id}", error);
    }

    @Test
    void loggingFailureDoesNotReplaceOriginalResponse() {
        doThrow(new IllegalStateException("database down")).when(logs)
                .record(anyString(), isNull(), anyString(), anyString(), any());
        var response = handler.handleGeneral(new RuntimeException(), new MockHttpServletResponse());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsKey("errorId");
    }

    @Test
    void recordsExplicitServerErrorsButSkipsExpectedClientErrors() {
        handler.handleResponseStatus(new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid"));
        handler.handleBadRequest(new IllegalArgumentException("invalid"));
        handler.handleAccessDenied(new org.springframework.security.access.AccessDeniedException("denied"));
        verifyNoInteractions(logs);
        var error = new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "unavailable");
        var response = handler.handleResponseStatus(error);
        assertThat(response.getBody()).containsKey("errorId");
        verify(logs).record(anyString(), isNull(), anyString(), anyString(), same(error));
    }
}
