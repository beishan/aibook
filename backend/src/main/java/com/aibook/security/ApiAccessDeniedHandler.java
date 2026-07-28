package com.aibook.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * REST API 已认证但权限不足响应。
 */
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private static final String RESPONSE_BODY =
            "{\"error\":\"Forbidden\",\"message\":\"没有权限执行此操作\"}";

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write(RESPONSE_BODY);
    }
}
