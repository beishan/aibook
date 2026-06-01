package com.aibook.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * WebDAV 方法过滤器
 * 将 PROPFIND/MKCOL/MOVE/COPY 等 WebDAV 自定义方法
 * 转换为 Spring MVC 可处理的标准方法
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class WebDavMethodFilter extends OncePerRequestFilter {

    private static final String WEBDAV_METHOD_HEADER = "X-WebDAV-Method";
    private static final String WEBDAV_METHOD_ATTR = "webdav.method";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // 只对 /webdav/** 路径处理
        if (!path.startsWith("/webdav")) {
            filterChain.doFilter(request, response);
            return;
        }

        String method = request.getMethod().toUpperCase();

        switch (method) {
            case "PROPFIND", "MKCOL", "MOVE", "COPY" -> {
                // 将自定义方法存入请求属性，并将请求方法改为 POST
                Map<String, String[]> newParams = new HashMap<>(request.getParameterMap());
                HttpServletRequest wrappedRequest = new HttpServletRequestWrapper(request) {
                    @Override
                    public String getMethod() {
                        return "POST";
                    }

                    @Override
                    public String getHeader(String name) {
                        if (WEBDAV_METHOD_HEADER.equalsIgnoreCase(name)) {
                            return method;
                        }
                        return super.getHeader(name);
                    }

                    @Override
                    public Map<String, String[]> getParameterMap() {
                        return newParams;
                    }
                };
                wrappedRequest.setAttribute(WEBDAV_METHOD_ATTR, method);
                log.debug("WebDAV method rewritten: {} -> POST (X-WebDAV-Method: {})", method, method);
                filterChain.doFilter(wrappedRequest, response);
            }
            default -> filterChain.doFilter(request, response);
        }
    }
}
