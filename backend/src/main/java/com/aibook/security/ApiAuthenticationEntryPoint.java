package com.aibook.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * REST API 未认证响应。
 */
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String RESPONSE_BODY =
            "{\"error\":\"Unauthorized\",\"message\":\"登录已失效，请重新登录\"}";

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write(RESPONSE_BODY);
    }
}
