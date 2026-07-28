package com.aibook.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityErrorHandlersTest {

    @Test
    void anonymousRequestReceivesUnauthorizedResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ApiAuthenticationEntryPoint().commence(
                new MockHttpServletRequest(),
                response,
                new BadCredentialsException("invalid token"));

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        assertTrue(response.getContentAsString().contains("登录已失效"));
    }

    @Test
    void authenticatedRequestWithoutPermissionReceivesForbiddenResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ApiAccessDeniedHandler().handle(
                new MockHttpServletRequest(),
                response,
                new AccessDeniedException("forbidden"));

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        assertTrue(response.getContentAsString().contains("没有权限"));
    }
}
