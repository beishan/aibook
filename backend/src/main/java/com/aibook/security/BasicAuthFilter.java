package com.aibook.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Basic Auth 认证过滤器
 * 用于 OPDS、WebDAV 和 Sync 等需要 Basic Auth 的端点
 * 注意：此 Bean 在 SecurityConfig 中通过 @Bean 方法创建，不使用 @Component
 */
@RequiredArgsConstructor
@Slf4j
public class BasicAuthFilter extends OncePerRequestFilter {

    private final AuthenticationManager authenticationManager;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // OPTIONS 请求不需要认证（用于 WebDAV 方法发现）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // 只对 OPDS、WebDAV 和 Sync 端点进行 Basic Auth
        if (path.startsWith("/opds") || path.startsWith("/webdav") || path.startsWith("/api/sync")) {
            String authHeader = request.getHeader("Authorization");

            // 如果已经有 Bearer token，跳过 Basic Auth，让 JwtAuthenticationFilter 处理
            if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
                filterChain.doFilter(request, response);
                return;
            }

            if (StringUtils.hasText(authHeader) && authHeader.startsWith("Basic ")) {
                String attemptedUsername = null;
                try {
                    String base64Credentials = authHeader.substring(6);
                    String credentials = new String(
                        Base64.getDecoder().decode(base64Credentials),
                        StandardCharsets.UTF_8
                    );
                    String[] parts = credentials.split(":", 2);

                    if (parts.length == 2) {
                        String username = parts[0];
                        String password = parts[1];
                        attemptedUsername = username;

                        UsernamePasswordAuthenticationToken authenticationRequest =
                            UsernamePasswordAuthenticationToken.unauthenticated(username, password);
                        authenticationRequest.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                        );
                        SecurityContextHolder.getContext().setAuthentication(
                            authenticationManager.authenticate(authenticationRequest)
                        );
                    }
                } catch (Exception e) {
                    log.warn(
                        "Basic Auth 认证失败: user={}, path={}, reason={}",
                        attemptedUsername != null ? attemptedUsername : "<无法解析>",
                        path,
                        e.getClass().getSimpleName()
                    );
                }
            }

            // 如果没有认证，返回 401
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setHeader("WWW-Authenticate", "Basic realm=\"Aibook\"");
                response.setHeader(
                    "X-Aibook-Auth-Status",
                    StringUtils.hasText(authHeader) && authHeader.startsWith("Basic ")
                        ? "invalid"
                        : "missing"
                );
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
