package com.aibook.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Base64;

/**
 * Basic Auth 认证过滤器
 * 用于 OPDS、WebDAV 和 Sync 等需要 Basic Auth 的端点
 * 注意：此 Bean 在 SecurityConfig 中通过 @Bean 方法创建，不使用 @Component
 */
@RequiredArgsConstructor
@Slf4j
public class BasicAuthFilter extends OncePerRequestFilter {

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

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
                try {
                    String base64Credentials = authHeader.substring(6);
                    String credentials = new String(Base64.getDecoder().decode(base64Credentials));
                    String[] parts = credentials.split(":", 2);

                    if (parts.length == 2) {
                        String username = parts[0];
                        String password = parts[1];

                        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                        if (passwordEncoder.matches(password, userDetails.getPassword())) {
                            UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                                );

                            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Basic Auth 认证失败: {}", e.getMessage());
                }
            }

            // 如果没有认证，返回 401
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setHeader("WWW-Authenticate", "Basic realm=\"Aibook\"");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
